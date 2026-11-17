package com.example.mediminder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mediminder.data.photos.MedicinePhotos
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicinePhotosTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, "medicine_photos").deleteRecursively()
    }

    private fun photo(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(3, 105, 161)) }

    @Test fun `a camera photo is stored small and privately`() = runBlocking {
        val ref = MedicinePhotos.save(context, photo(4000, 3000))
        val file = MedicinePhotos.file(context, ref)
        assertNotNull(file)
        assertTrue("inside the app's private files", file!!.path.startsWith(context.filesDir.path))
        val stored = MedicinePhotos.picture(context, ref, maxSide = 4000)!!
        assertEquals("shrunk to 1280 px", 1280, maxOf(stored.width, stored.height))
        assertEquals(960, minOf(stored.width, stored.height))
    }

    @Test fun `notification thumbnail is a square of the asked size`() = runBlocking {
        val ref = MedicinePhotos.save(context, photo(1200, 800))
        val thumb = MedicinePhotos.thumbnail(context, ref, size = 256)!!
        assertEquals(256, thumb.width)
        assertEquals(256, thumb.height)
    }

    @Test fun `a gallery photo taken sideways is turned upright`() = runBlocking {
        // A portrait photo stored landscape with an EXIF "rotate 90" tag, as phones do
        val source = File(context.cacheDir, "sideways.jpg")
        source.outputStream().use { photo(800, 600).compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(source.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val ref = MedicinePhotos.import(context, Uri.fromFile(source))!!
        val upright = MedicinePhotos.picture(context, ref, maxSide = 2000)!!
        assertEquals(600, upright.width)
        assertEquals(800, upright.height)
    }

    @Test fun `missing or unreadable photos fall back quietly`() {
        assertNull(MedicinePhotos.model(context, null))
        assertNull(MedicinePhotos.model(context, "gone.jpg"))
        assertNull(MedicinePhotos.thumbnail(context, "gone.jpg"))
        assertNull("no path tricks", MedicinePhotos.file(context, "../databases/mediminder.db"))
    }

    @Test fun `unused photos are tidied, recent and used ones kept`() = runBlocking {
        val used = MedicinePhotos.save(context, photo(100, 100))
        val oldUnused = MedicinePhotos.save(context, photo(100, 100))
        val newUnused = MedicinePhotos.save(context, photo(100, 100))
        val twoHoursAgo = System.currentTimeMillis() - 2 * 60 * 60_000
        MedicinePhotos.file(context, used)!!.setLastModified(twoHoursAgo)
        MedicinePhotos.file(context, oldUnused)!!.setLastModified(twoHoursAgo)

        MedicinePhotos.removeUnused(context, setOf(used))

        assertNotNull(MedicinePhotos.file(context, used))
        assertNull(MedicinePhotos.file(context, oldUnused))
        assertNotNull("an editor may still be open", MedicinePhotos.file(context, newUnused))
        MedicinePhotos.delete(context, used)
        assertFalse(File(context.filesDir, "medicine_photos/$used").exists())
    }
}
