package com.example.mediminder.data.photos

import com.example.mediminder.util.ErrorLog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Photos of a medicine's box or strip, kept privately in the app's files
 * (files/medicine_photos/<id>.jpg). `Medication.photoUri` stores just the file name;
 * a content:// or file:// value (from 1.x) is still shown if it can be opened.
 *
 * Photos are shrunk to [MAX_SIDE] px (~150 KB) so a dozen medicines cost very little
 * space and fit Android's own cloud backup.
 */
object MedicinePhotos {
    private const val DIR = "medicine_photos"
    private const val MAX_SIDE = 1280
    private const val QUALITY = 85
    private const val TAG = "MedicinePhotos"

    private fun dir(context: Context) = File(context.filesDir, DIR).apply { mkdirs() }

    private fun isExternal(ref: String) = ref.startsWith("content:") || ref.startsWith("file:")

    /** Our stored file for [ref], or null if there is none on this phone. */
    fun file(context: Context, ref: String?): File? {
        if (ref.isNullOrBlank() || isExternal(ref) || ref.contains('/')) return null
        return File(dir(context), ref).takeIf { it.isFile }
    }

    /** What an image loader should show for [ref], or null to fall back to the colour badge. */
    fun model(context: Context, ref: String?): Any? = when {
        ref.isNullOrBlank() -> null
        isExternal(ref) -> Uri.parse(ref)
        else -> file(context, ref)
    }

    private fun open(context: Context, ref: String): InputStream? = runCatching {
        if (isExternal(ref)) context.contentResolver.openInputStream(Uri.parse(ref)) else file(context, ref)?.inputStream()
    }.getOrNull()

    /** Saves a camera frame (already upright) and returns its reference. */
    suspend fun save(context: Context, bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val scaled = scaleDown(bitmap, MAX_SIDE)
        val name = UUID.randomUUID().toString() + ".jpg"
        File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
        if (scaled !== bitmap) scaled.recycle()
        name
    }

    /** Copies a picture chosen from the gallery, upright and shrunk. Null if it can't be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decode({ context.contentResolver.openInputStream(uri) }, MAX_SIDE) ?: return@withContext null
            save(context, bitmap).also { bitmap.recycle() }
        } catch (e: Exception) {
            ErrorLog.record("Photos", "Could not import a gallery picture", e)
            null
        }
    }

    fun delete(context: Context, ref: String?) {
        file(context, ref)?.delete()
    }

    /** Removes photos no medicine uses any more (e.g. an editor that was left without saving). */
    suspend fun removeUnused(context: Context, inUse: Set<String>) = withContext(Dispatchers.IO) {
        val hourAgo = System.currentTimeMillis() - 60 * 60_000
        dir(context).listFiles()?.forEach { f ->
            // Recent files may belong to an editor that is still open
            if (f.name !in inUse && f.lastModified() < hourAgo) f.delete()
        }
    }

    /** A square, centre-cropped thumbnail for a notification's side picture. */
    fun thumbnail(context: Context, ref: String?, size: Int = 256): Bitmap? {
        if (ref.isNullOrBlank()) return null
        val bitmap = decode({ open(context, ref) }, size * 2) ?: return null
        val side = min(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val out = Bitmap.createScaledBitmap(square, size, size, true)
        if (square !== bitmap) bitmap.recycle()
        if (out !== square) square.recycle()
        return out
    }

    /** The photo at notification size, for the expanded "big picture". */
    fun picture(context: Context, ref: String?, maxSide: Int = 960): Bitmap? {
        if (ref.isNullOrBlank()) return null
        return decode({ open(context, ref) }, maxSide)?.let { scaleDown(it, maxSide) }
    }

    // ── Decoding ────────────────────────────────────────────────────────────

    /** Decodes at roughly [maxSide], turned upright according to the photo's EXIF data. */
    private fun decode(stream: () -> InputStream?, maxSide: Int): Bitmap? {
        try {
            // Bounds only (decodeStream returns null here by design), then a sampled decode
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            (stream() ?: return null).use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val bitmap = (stream() ?: return null).use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            val degrees = stream()?.use { orientation(it) } ?: 0
            if (degrees == 0) return bitmap
            val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
            if (upright !== bitmap) bitmap.recycle()
            return upright
        } catch (e: Exception) {
            ErrorLog.record("Photos", "Could not decode a photo", e)
            return null
        } catch (e: OutOfMemoryError) {
            return null
        }
    }

    private fun orientation(input: InputStream): Int = runCatching {
        when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSide) return bitmap
        val f = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).roundToInt(), (bitmap.height * f).roundToInt(), true)
    }
}
