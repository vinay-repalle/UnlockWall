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

/** Runs on the phone: drawing and decoding need real Android. Works in its own temp folder. */
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

    /** Red left third, green middle, blue right third. */
    private fun photo(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        listOf(Color.RED, Color.GREEN, Color.BLUE).forEachIndexed { index, color ->
            paint.color = color
            canvas.drawRect(width * index / 3f, 0f, width * (index + 1) / 3f, height.toFloat(), paint)
        }
        return bitmap
    }

    private fun stored(slot: Slot): Bitmap = BitmapFactory.decodeFile(cache.file(slot)!!.path)

    private fun colorAt(bitmap: Bitmap, x: Int, y: Int): String {
        val pixel = bitmap.getPixel(x, y)
        val parts = mapOf(
            "red" to Color.red(pixel),
            "green" to Color.green(pixel),
            "blue" to Color.blue(pixel),
        )
        return parts.maxBy { it.value }.key
    }

    @Test
    fun emptyCacheHasNothing() {
        assertEquals(emptySet<Slot>(), cache.available())
        assertNull(cache.file(Slot.NORMAL))
    }

    @Test
    fun widePhotoKeepsTheMiddleAtScreenSize() {
        assertTrue(cache.save(Slot.NORMAL, photo(1200, 400)))
        val result = stored(Slot.NORMAL)
        assertEquals(WIDTH to HEIGHT, result.width to result.height)
        assertEquals("green", colorAt(result, WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun draggingChoosesAnotherPart() {
        assertTrue(cache.save(Slot.NORMAL, photo(1200, 400), Adjustment(panX = 99f)))
        assertEquals("red", colorAt(stored(Slot.NORMAL), WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun rotatingTurnsTheLeftSideToTheTop() {
        assertTrue(cache.save(Slot.NORMAL, photo(1200, 540), Adjustment(rotation = 90)))
        val result = stored(Slot.NORMAL)
        assertEquals("red", colorAt(result, WIDTH / 2, 5))
        assertEquals("blue", colorAt(result, WIDTH / 2, HEIGHT - 5))
    }

    @Test
    fun theOtherRotationsAlsoFillTheScreen() {
        assertTrue(cache.save(Slot.NORMAL, photo(1200, 540), Adjustment(rotation = 270)))
        assertEquals("blue", colorAt(stored(Slot.NORMAL), WIDTH / 2, 5))
        assertTrue(cache.save(Slot.NORMAL, photo(540, 1200), Adjustment(rotation = 180)))
        val upsideDown = stored(Slot.NORMAL)
        assertEquals("blue", colorAt(upsideDown, 5, HEIGHT / 2))
        assertEquals("red", colorAt(upsideDown, WIDTH - 5, HEIGHT / 2))
    }

    @Test
    fun slotsAreKeptApart() {
        cache.save(Slot.NORMAL, photo(200, 400))
        cache.save(Slot.SLOW, photo(400, 200))
        assertEquals(setOf(Slot.NORMAL, Slot.SLOW), cache.available())
    }

    @Test
    fun failedSaveKeepsTheOldPicture() {
        cache.save(Slot.NORMAL, photo(200, 400))
        val before = cache.file(Slot.NORMAL)!!.readBytes()
        // A folder where the temporary file should go makes writing fail.
        assertTrue(File(directory, "normal.tmp").mkdir())
        assertFalse(cache.save(Slot.NORMAL, photo(400, 200)))
        assertArrayEquals(before, cache.file(Slot.NORMAL)!!.readBytes())
        assertFalse(File(directory, "normal.tmp").exists())
    }

    @Test
    fun recycledPictureIsRefused() {
        assertFalse(cache.save(Slot.NORMAL, photo(200, 400).apply { recycle() }))
        assertNull(cache.file(Slot.NORMAL))
    }

    @Test
    fun brokenFileCannotBeDecoded() {
        val broken = File(work, "broken.jpg").apply { writeText("not a picture") }
        assertNull(Pictures.decode(ImageDecoder.createSource(broken)))
    }

    @Test
    fun hugePhotoIsShrunkWhenDecoded() {
        val huge = File(work, "huge.png")
        huge.outputStream().use { photo(6000, 100).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val decoded = Pictures.decode(ImageDecoder.createSource(huge))
        assertNotNull(decoded)
        assertEquals(4096, decoded!!.width)
    }

    @Test
    fun emptyFileDoesNotCount() {
        directory.mkdirs()
        File(directory, "quick.jpg").createNewFile()
        assertNull(cache.file(Slot.QUICK))
    }

    @Test
    fun removeEmptiesTheSlot() {
        cache.save(Slot.QUICK, photo(200, 400))
        cache.remove(Slot.QUICK)
        assertEquals(emptySet<Slot>(), cache.available())
        assertNull(cache.editable(Slot.QUICK))
        assertEquals(0, directory.listFiles()!!.size)
    }

    @Test
    fun wholePhotoAndPlacementAreKept() {
        val adjustment = Adjustment(zoom = 2f, panX = 0.1f)
        cache.save(Slot.NORMAL, photo(1200, 400), adjustment)
        val (file, saved) = cache.editable(Slot.NORMAL)!!
        assertEquals(adjustment, saved)
        // The whole photo, not the cropped screen picture.
        assertEquals(1200, BitmapFactory.decodeFile(file.path).width)
    }

    @Test
    fun adjustingAgainDoesNotRewriteThePhoto() {
        cache.save(Slot.NORMAL, photo(1200, 400))
        val file = cache.editable(Slot.NORMAL)!!.first
        val before = file.readBytes()
        val again = BitmapFactory.decodeFile(file.path)
        assertTrue(cache.save(Slot.NORMAL, again, Adjustment(panX = 99f), newPhoto = false))
        assertArrayEquals(before, file.readBytes())
        assertEquals("red", colorAt(stored(Slot.NORMAL), WIDTH / 2, HEIGHT / 2))
    }

    @Test
    fun aSlotWithoutAKeptPhotoCanStillBeAdjusted() {
        cache.save(Slot.NORMAL, photo(200, 400))
        File(directory, "normal.photo.jpg").delete()
        val (file, saved) = cache.editable(Slot.NORMAL)!!
        assertEquals("normal.jpg" to Adjustment(), file.name to saved)
    }

    private companion object {
        const val WIDTH = 108
        const val HEIGHT = 240
    }
}
