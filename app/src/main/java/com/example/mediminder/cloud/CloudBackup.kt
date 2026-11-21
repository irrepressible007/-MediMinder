package com.example.mediminder.cloud

import android.content.Context
import android.os.Build
import com.example.mediminder.BuildConfig
import com.example.mediminder.data.backup.BackupCounts
import com.example.mediminder.data.backup.BackupManager
import com.example.mediminder.data.settings.SettingsRepository
import com.example.mediminder.util.ErrorLog
import com.example.mediminder.util.await
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** This build's sign-in settings (empty when the build has none: sign-in is then hidden). */
object CloudConfig {
    val available: Boolean get() = BuildConfig.FIREBASE_API_KEY.isNotBlank() && BuildConfig.FIREBASE_APP_ID.isNotBlank()
    val google: Boolean get() = available && BuildConfig.FIREBASE_WEB_CLIENT_ID.isNotBlank()

    /** Starts Firebase from the build's settings (there is no google-services plugin). */
    fun init(context: Context) {
        if (!available || FirebaseApp.getApps(context).isNotEmpty()) return
        try {
            FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setApiKey(BuildConfig.FIREBASE_API_KEY)
                    .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                    .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                    .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                    .setStorageBucket(BuildConfig.FIREBASE_STORAGE_BUCKET)
                    .build(),
            )
            // the backup lives on the phone already: no second copy in Firestore's cache
            FirebaseFirestore.getInstance().firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        } catch (e: Exception) {
            ErrorLog.record("Cloud", "Firebase could not start", e)
        }
    }
}

/** When the cloud copy was made, and from which phone. */
data class CloudCopy(val savedAt: Long, val device: String, val appVersion: String, val parts: Int)

/**
 * The cloud copy of a person's MediMinder data: the same content as a backup file, gzipped
 * and kept in Firestore under users/{uid}, in parts small enough for Firestore documents.
 * Only that user can read or write it (see the Firestore rules in the README).
 */
@Singleton
class CloudBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backups: BackupManager,
    private val settings: SettingsRepository,
) {
    private val uid: String? get() = if (CloudConfig.available) FirebaseAuth.getInstance().currentUser?.uid else null

    private fun meta(uid: String) = FirebaseFirestore.getInstance().collection("users").document(uid).collection("backup").document("latest")
    private fun part(uid: String, i: Int) = FirebaseFirestore.getInstance().collection("users").document(uid).collection("backupParts").document("p$i")

    /** The cloud copy, if there is one. */
    suspend fun latest(): CloudCopy? {
        val id = uid ?: return null
        val doc = meta(id).get().await()
        if (!doc.exists()) return null
        return CloudCopy(
            savedAt = doc.getLong("savedAt") ?: 0,
            device = doc.getString("device").orEmpty(),
            appVersion = doc.getString("appVersion").orEmpty(),
            parts = (doc.getLong("parts") ?: 0).toInt(),
        )
    }

    /** Saves this phone's data as the cloud copy. Returns when it was saved. */
    suspend fun upload(): Long {
        val id = uid ?: error("Not signed in")
        val previous = runCatching { latest()?.parts }.getOrNull() ?: 0
        val parts = withContext(Dispatchers.Default) { pack(backups.encode(backups.create())) }
        val now = System.currentTimeMillis()
        val db = FirebaseFirestore.getInstance()
        val batch = db.batch()
        parts.forEachIndexed { i, bytes -> batch.set(part(id, i), mapOf("data" to Blob.fromBytes(bytes))) }
        for (i in parts.size until previous) batch.delete(part(id, i))
        batch.set(
            meta(id),
            mapOf("savedAt" to now, "parts" to parts.size, "device" to "${Build.MANUFACTURER} ${Build.MODEL}", "appVersion" to BuildConfig.VERSION_NAME, "format" to 1),
            SetOptions.merge(),
        )
        batch.commit().await()
        settings.update { it.copy(lastBackupAt = now) }
        return now
    }

    /** Replaces this phone's data with the cloud copy. */
    suspend fun restore(): BackupCounts {
        val id = uid ?: error("Not signed in")
        val copy = latest() ?: error("No cloud copy")
        val bytes = (0 until copy.parts).map { i -> part(id, i).get().await().getBlob("data")?.toBytes() ?: error("Missing part $i") }
        val backup = backups.decode(withContext(Dispatchers.Default) { unpack(bytes) })
        return backups.restore(backup)
    }

    /** Removes the cloud copy (before deleting the account, or on request). */
    suspend fun deleteAll() {
        val id = uid ?: return
        val parts = runCatching { latest()?.parts }.getOrNull() ?: 0
        val batch = FirebaseFirestore.getInstance().batch()
        for (i in 0 until maxOf(parts, 1)) batch.delete(part(id, i))
        batch.delete(meta(id))
        batch.commit().await()
    }

    companion object {
        /** Firestore allows 1 MiB a document; parts stay well under it. */
        const val PART_BYTES = 700_000

        fun pack(text: String): List<ByteArray> {
            val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(text.toByteArray()) } }.toByteArray()
            return (zipped.indices step PART_BYTES).map { zipped.copyOfRange(it, minOf(it + PART_BYTES, zipped.size)) }
                .ifEmpty { listOf(ByteArray(0)) }
        }

        fun unpack(parts: List<ByteArray>): String {
            val all = ByteArrayOutputStream().also { out -> parts.forEach(out::write) }.toByteArray()
            return GZIPInputStream(ByteArrayInputStream(all)).use { it.readBytes().decodeToString() }
        }
    }
}
