package com.example.mediminder.updater

import android.app.PendingIntent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import com.example.mediminder.util.ErrorLog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.FileProvider
import com.example.mediminder.BuildConfig
import com.example.mediminder.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/*
 * Updates come from this app's GitHub releases.
 *
 *  • On opening the app it checks (at most every 12 hours) and, when a newer build exists,
 *    recommends it in a friendly dialog. "Later" quiets that version for 3 days.
 *  • Newer means a higher versionCode, read from the release's update.json (written by the
 *    release workflow) — never the version name, so renumbering can't hide an update.
 *  • The APK is downloaded inside the app with a progress bar, checked against its size and
 *    SHA-256, then installed by the app itself through Android's PackageInstaller; Android
 *    still asks the person to confirm. If Android needs the one-time "install unknown apps"
 *    permission, the dialog explains it and opens the right settings page.
 *  • Why not hand the file to Android's installer app: on a Pixel 6 (Android 17) every update
 *    installed that way switched MediMinder's full-screen alarm permission off. Installs
 *    through PackageInstaller kept it.
 */

/** The newest release on GitHub. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int?,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String?,
    val notes: List<String>,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    /** [progress] 0..1, or -1 while the size is unknown. */
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Ready(val info: UpdateInfo, val file: File, val needsPermission: Boolean = false) : UpdateState
    data class Failed(val info: UpdateInfo?, @StringRes val reason: Int) : UpdateState
}

object Updater {
    private const val REPO = "https://github.com/irrepressible007/-MediMinder"

    /** An APK link from this app's own GitHub releases (https only). */
    internal fun isOwnRelease(url: String): Boolean = url.startsWith("$REPO/releases/download/") && !url.contains("..")

    /** The downloaded file is this app (same package) at the expected version; Android checks the signature. */
    private fun checkPackage(context: Context, apk: File, versionCode: Int?) {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: throw IOException("not an APK")
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        if (info.packageName != context.packageName) throw IOException("another app: ${info.packageName}")
        if (versionCode != null && code != versionCode.toLong()) throw IOException("version $code, expected $versionCode")
    }
    /** The newest release's update.json via github.com's download links: no API, so no shared per-address limit. */
    private const val LATEST_META = "$REPO/releases/latest/download/update.json"
    /** Fallback for releases made before update.json. The API allows 60 anonymous checks an hour per internet
     *  address, and many people in Bangladesh share one address — so it is never the first choice. */
    private const val LATEST_API = "https://api.github.com/repos/irrepressible007/-MediMinder/releases/latest"
    private const val PREFS = "mediminder_updates"
    private const val CHECK_EVERY = 12 * 60 * 60_000L
    private const val LATER_FOR = 3 * 24 * 60 * 60_000L
    private const val TAG = "MediMinderUpdate"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var download: Job? = null

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private val _dialog = MutableStateFlow(false)
    /** Whether the update dialog is showing. */
    val dialog: StateFlow<Boolean> = _dialog

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Quiet check when the app opens; recommends the update unless "Later" was chosen recently. */
    fun checkOnLaunch(context: Context) {
        if (BuildConfig.DEBUG) return   // developer builds (and tests) never call home
        val app = context.applicationContext
        val prefs = prefs(app)
        if (System.currentTimeMillis() - prefs.getLong("lastCheck", 0) < CHECK_EVERY) return
        if (_state.value !is UpdateState.Idle && _state.value !is UpdateState.UpToDate) return
        scope.launch {
            val info = runCatching { fetchLatest() }.onFailure { ErrorLog.record("Update", "Automatic update check failed", it) }.getOrNull() ?: return@launch
            prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
            if (!isUpdate(info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) return@launch
            _state.value = UpdateState.Available(info)
            val laterUntil = prefs.getLong("later:${info.versionName}", 0)
            if (System.currentTimeMillis() > laterUntil) _dialog.value = true
        }
    }

    /** "Check for updates" on More: always checks, always answers. */
    fun checkNow() {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Ready -> { _dialog.value = true; return }
            else -> Unit
        }
        _state.value = UpdateState.Checking
        _dialog.value = true
        scope.launch {
            _state.value = try {
                val info = fetchLatest()
                if (isUpdate(info, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                ErrorLog.record("Update", "Update check failed", e)
                UpdateState.Failed(null, R.string.update_check_failed)
            }
        }
    }

    /** "Later": hide, and don't recommend this version again for a few days. */
    fun later(context: Context) {
        (_state.value as? UpdateState.Available)?.let {
            prefs(context).edit().putLong("later:${it.info.versionName}", System.currentTimeMillis() + LATER_FOR).apply()
        }
        _dialog.value = false
    }

    fun hide() {
        _dialog.value = false
        if (_state.value is UpdateState.UpToDate || _state.value is UpdateState.Checking) _state.value = UpdateState.Idle
    }

    fun startDownload(context: Context, info: UpdateInfo) {
        if (download?.isActive == true) return
        val dir = File(context.applicationContext.cacheDir, "updates").apply { mkdirs() }
        _state.value = UpdateState.Downloading(info, if (info.apkSize > 0) 0f else -1f)
        _dialog.value = true
        download = scope.launch {
            dir.listFiles()?.forEach { it.delete() }   // earlier downloads
            val part = File(dir, "MediMinder-${info.versionName}.apk.part")
            val apk = File(dir, "MediMinder-${info.versionName}.apk")
            _state.value = try {
                // only ever from this app's own GitHub releases
                if (!isOwnRelease(info.apkUrl)) throw IOException("not a MediMinder release: ${info.apkUrl}")
                val sha = downloadTo(info.apkUrl, part, info.apkSize) { p -> _state.value = UpdateState.Downloading(info, p) }
                if (info.sha256 != null && !sha.equals(info.sha256, ignoreCase = true)) throw IOException("checksum mismatch")
                if (info.apkSize > 0 && part.length() != info.apkSize) throw IOException("size mismatch")
                if (!part.renameTo(apk)) throw IOException("rename failed")
                // and it must be MediMinder itself, at the version the release announced
                checkPackage(context, apk, info.versionCode)
                _dialog.value = true
                UpdateState.Ready(info, apk)
            } catch (e: Exception) {
                ErrorLog.record("Update", "Update download failed", e)
                part.delete()
                UpdateState.Failed(info, R.string.update_download_failed)
            }
        }
    }

    /**
     * Installs the APK (Android shows its "Update this app?" confirmation), or — when Android
     * needs the one-time permission — asks for it first. Call again after the person comes back
     * from settings. Answers arrive in [InstallResultReceiver].
     */
    fun install(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            _state.value = ready.copy(needsPermission = true)
            return
        }
        _state.value = ready.copy(needsPermission = false)
        val app = context.applicationContext
        scope.launch {
            try {
                commitSession(app, ready.file)
            } catch (e: Exception) {
                // the older way still installs; it just goes through Android's installer app
                ErrorLog.record("Update", "Could not start the install, opening Android's installer instead", e)
                withContext(Dispatchers.Main) { openInstallerApp(app, ready) }
            }
        }
    }

    /** Copies the APK into an install session and commits it. */
    @androidx.annotation.VisibleForTesting
    internal fun commitSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // Android fills in the result, so the PendingIntent has to be mutable; it is
                // explicit, aimed at a receiver nothing else can reach
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
                session.commit(result.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Android refused the update, or something broke on the way: say so, and log why. */
    internal fun installFailed(why: String, error: Throwable? = null) {
        ErrorLog.record("Update", "Install failed: $why", error)
        _state.value = UpdateState.Failed((_state.value as? UpdateState.Ready)?.info, R.string.update_install_failed)
        _dialog.value = true
    }

    /** Tests start from a downloaded, checked update. */
    @androidx.annotation.VisibleForTesting
    internal fun readyForTest(info: UpdateInfo, file: File) { _state.value = UpdateState.Ready(info, file) }

    /** The person tapped Cancel on Android's confirmation: still ready to install. */
    internal fun installCancelled() {
        _dialog.value = true
    }

    /** The older route: Android's installer app opens the file. */
    private fun openInstallerApp(context: Context, ready: UpdateState.Ready) {
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", ready.file)
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            _state.value = ready.copy(needsPermission = false)
        } catch (e: Exception) {
            ErrorLog.record("Update", "Could not open the installer", e)
            _state.value = UpdateState.Failed(ready.info, R.string.update_install_failed)
        }
    }

    /** Opens the "install unknown apps" switch for MediMinder. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** Back from settings: if the permission is now on, carry straight on with the install. */
    fun onResume(context: Context) {
        val ready = _state.value as? UpdateState.Ready ?: return
        if (ready.needsPermission && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())) {
            install(context)
        }
    }

    // ── Talking to GitHub ───────────────────────────────────────────────────

    private suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        runCatching { parseMeta(get(LATEST_META)).toInfo(REPO) }
            .onFailure { Log.i(TAG, "update.json not reachable, asking the API", it) }
            .getOrNull()
            ?: run {
                val release = parseRelease(get(LATEST_API))
                val meta = release.metaUrl?.let { url -> runCatching { parseMeta(get(url)) }.getOrNull() }
                release.toInfo(meta) ?: throw IOException("no APK in the latest release")
            }
    }

    private fun get(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            instanceFollowRedirects = true   // github.com hands files over from another host
            if (url.startsWith("https://api.github.com")) setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "MediMinder-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    /** Streams [url] into [dest], reporting progress; returns the file's SHA-256 (hex). */
    private suspend fun downloadTo(url: String, dest: File, expectedSize: Long, onProgress: (Float) -> Unit): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true   // GitHub hands the file over from another host
            setRequestProperty("User-Agent", "MediMinder-Android")
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var lastReported = -1
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        } finally {
            conn.disconnect()
        }
    }
}

// ── Parsing (pure, so it can be tested) ─────────────────────────────────────

internal data class Release(val tag: String, val body: String, val apkUrl: String?, val apkSize: Long, val metaUrl: String?)

internal data class UpdateMeta(
    val versionCode: Int,
    val versionName: String,
    val sha256: String?,
    val size: Long,
    val apk: String? = null,
    val notes: List<String> = emptyList(),
)

internal fun parseRelease(json: String): Release {
    val o = JSONObject(json)
    val assets = o.optJSONArray("assets")
    var apkUrl: String? = null
    var apkSize = 0L
    var metaUrl: String? = null
    for (i in 0 until (assets?.length() ?: 0)) {
        val a = assets!!.getJSONObject(i)
        val name = a.optString("name")
        when {
            name.endsWith(".apk") && apkUrl == null -> {
                apkUrl = a.optString("browser_download_url")
                apkSize = a.optLong("size")
            }
            name == "update.json" -> metaUrl = a.optString("browser_download_url")
        }
    }
    return Release(o.optString("tag_name"), o.optString("body"), apkUrl, apkSize, metaUrl)
}

internal fun parseMeta(json: String): UpdateMeta {
    val o = JSONObject(json)
    val notes = o.optJSONArray("notes")
    return UpdateMeta(
        versionCode = o.getInt("versionCode"),
        versionName = o.optString("versionName"),
        sha256 = o.optString("sha256").ifBlank { null },
        size = o.optLong("size"),
        apk = o.optString("apk").ifBlank { null },
        notes = List(notes?.length() ?: 0) { notes!!.optString(it) }.filter { it.isNotBlank() },
    )
}

/** update.json on its own is enough: it names the APK (or it sits under the release's tag). */
internal fun UpdateMeta.toInfo(repo: String): UpdateInfo = UpdateInfo(
    versionName = versionName,
    versionCode = versionCode,
    apkUrl = apk ?: "$repo/releases/download/v$versionName/MediMinder-$versionName.apk",
    apkSize = size,
    sha256 = sha256,
    notes = cleanNotes(notes.joinToString(System.lineSeparator())),
)

internal fun Release.toInfo(meta: UpdateMeta?): UpdateInfo? {
    val url = apkUrl ?: return null
    return UpdateInfo(
        versionName = meta?.versionName?.ifBlank { null } ?: tag.trimStart('v', 'V'),
        versionCode = meta?.versionCode,
        apkUrl = url,
        apkSize = if (meta != null && meta.size > 0) meta.size else apkSize,
        sha256 = meta?.sha256,
        notes = meta?.notes?.takeIf { it.isNotEmpty() }?.let { cleanNotes(it.joinToString(System.lineSeparator())) } ?: cleanNotes(body),
    )
}

/** A higher versionCode wins; releases without update.json fall back to comparing names. */
internal fun isUpdate(info: UpdateInfo, currentCode: Int, currentName: String): Boolean =
    if (info.versionCode != null) info.versionCode > currentCode else isNewer(info.versionName, currentName)

/** The release text as short plain lines — without GitHub's automatic changelog links. */
internal fun cleanNotes(body: String): List<String> = body.lines()
    .map { it.trim() }
    .filter { it.isNotEmpty() && !it.contains("Full Changelog", true) && !it.contains("What's Changed", true) && !it.startsWith("#") && !it.contains("http") }
    .map { it.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim() }
    .take(12)

/** "v2.1.0" > "v2.0.10"? Compares numeric parts, so an older release is never offered as an update. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trimStart('v', 'V').split('.', '-').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
