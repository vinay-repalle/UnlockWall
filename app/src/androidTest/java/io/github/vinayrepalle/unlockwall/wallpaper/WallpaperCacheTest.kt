package io.github.vinayrepalle.unlockwall.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.File
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on the phone: image decoding needs real Android. Works in its own temp folder. */
@RunWith(AndroidJUnit4::class)
class WallpaperCacheTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val work = File(context.cacheDir, "cache-test").apply {
        deleteRecursively()
        mkdirs()
    }
    private val directory = File(work, "wallpapers")
    private val cache = WallpaperCache(directory, WIDTH, HEIGHT)

    @After
    fun cleanUp() {
        work.deleteRecursively()
    }

    /** Red left third, green middle, red right third. */
    private fun photo(width: Int, height: Int, name: String = "photo.png"): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.RED)
        val paint = Paint().apply { color = Color.GREEN }
        canvas.drawRect(width / 3f, 0f, width * 2 / 3f, height.toFloat(), paint)
        return File(work, name).also { file ->
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun source(file: File) = ImageDecoder.createSource(file)

    @Test
    fun emptyCacheHasNothing() {
        assertEquals(emptySet<Slot>(), cache.available())
        assertNull(cache.file(Slot.NORMAL))
    }

    @Test
    fun widePhotoIsScaledAndCroppedToTheScreen() {
        assertTrue(cache.save(Slot.NORMAL, source(photo(1200, 400))))
        val stored = BitmapFactory.decodeFile(cache.file(Slot.NORMAL)!!.path)
        assertEquals(WIDTH, stored.width)
        assertEquals(HEIGHT, stored.height)
        // The middle of a wide photo is kept, so the centre is green.
        val centre = stored.getPixel(WIDTH / 2, HEIGHT / 2)
        assertTrue(Color.green(centre) > 200 && Color.red(centre) < 60)
    }

    @Test
    fun smallPhotoIsEnlarged() {
        assertTrue(cache.save(Slot.QUICK, source(photo(50, 50))))
        val stored = BitmapFactory.decodeFile(cache.file(Slot.QUICK)!!.path)
        assertEquals(WIDTH to HEIGHT, stored.width to stored.height)
    }

    @Test
    fun slotsAreKeptApart() {
        cache.save(Slot.NORMAL, source(photo(200, 400, "a.png")))
        cache.save(Slot.SLOW, source(photo(400, 200, "b.png")))
        assertEquals(setOf(Slot.NORMAL, Slot.SLOW), cache.available())
    }

    @Test
    fun pictureStaysAfterTheOriginalIsDeleted() {
        val original = photo(200, 400)
        cache.save(Slot.NORMAL, source(original))
        assertTrue(original.delete())
        assertNotNull(cache.file(Slot.NORMAL))
    }

    @Test
    fun brokenPhotoKeepsTheOldPicture() {
        cache.save(Slot.NORMAL, source(photo(200, 400)))
        val before = cache.file(Slot.NORMAL)!!.readBytes()
        val broken = File(work, "broken.jpg").apply { writeText("not a picture") }
        assertFalse(cache.save(Slot.NORMAL, source(broken)))
        assertArrayEquals(before, cache.file(Slot.NORMAL)!!.readBytes())
        assertFalse(File(directory, "normal.tmp").exists())
    }

    @Test
    fun emptyFileDoesNotCount() {
        directory.mkdirs()
        File(directory, "quick.jpg").createNewFile()
        assertNull(cache.file(Slot.QUICK))
    }

    @Test
    fun removeEmptiesTheSlot() {
        cache.save(Slot.QUICK, source(photo(200, 400)))
        cache.remove(Slot.QUICK)
        assertEquals(emptySet<Slot>(), cache.available())
    }

    private companion object {
        const val WIDTH = 108
        const val HEIGHT = 240
    }
}
