package com.example.mediminder.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.mediminder.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The app's own problem log: crashes and the problems the app catches (an update that could not
 * be fetched, a reminder that failed in the background, a photo that could not be read…).
 *
 * Kept in the app's external files folder (Android/data/com.example.mediminder/files/logs) so a
 * developer can pull it in one step, and shown in Settings → Problem log, where the person can
 * share it (WhatsApp, email) or clear it. Capped at ~96 KB, keeping the newest entries.
 */
object ErrorLog {
    private const val MAX_BYTES = 96 * 1024
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val ENTRY = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2} .*""")

    @Volatile private var file: File? = null

    /** Called once when the app starts: opens the log and records crashes from then on. */
    fun init(context: Context) {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs").apply { mkdirs() }
        file = File(dir, "problems.log")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record("Crash", "The app stopped (${thread.name})", error)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Writes one problem, with the first lines of its cause; also goes to logcat. */
    fun record(area: String, message: String, error: Throwable? = null) {
        Log.w("MediMinder/$area", message, error)
        val f = file ?: return
        runCatching {
            synchronized(this) {
                if (f.length() > MAX_BYTES) {
                    // keep the newer half, starting at a whole entry
                    val text = f.readText()
                    f.writeText(text.substring(text.length / 2).substringAfter('\n'))
                }
                val cause = error?.let { e ->
                    (listOf(e.toString()) + e.stackTrace.take(6).map { "at $it" } +
                        listOfNotNull(e.cause?.let { "caused by $it" })).joinToString("\n    ", prefix = "\n    ")
                }.orEmpty()
                f.appendText("${LocalDateTime.now().format(TIME)}  [$area] $message$cause\n")
            }
        }
    }

    fun read(): String = runCatching { file?.takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    /** How many problems are in the log. */
    fun count(): Int = read().lineSequence().count { ENTRY.matches(it) }

    /** When the newest problem happened ("2026-11-21 05:09"), or null. */
    fun lastAt(): String? = read().lineSequence().lastOrNull { ENTRY.matches(it) }?.take(16)

    /** The log with what a developer needs to know about the phone, ready to send. */
    fun report(): String = buildString {
        appendLine("MediMinder ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine()
        append(read().ifBlank { "No problems recorded." })
    }

    fun clear() {
        runCatching { file?.writeText("") }
    }
}
