package io.github.vinayrepalle.unlockwall.wallpaper

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Only the failure paths: these must return before the real wallpaper is touched (T-13). */
@RunWith(AndroidJUnit4::class)
class WallpaperApplierTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "applier-test").apply {
        deleteRecursively()
        mkdirs()
    }
    private val applier = WallpaperApplier(context, WallpaperCache(directory, 108, 240))

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun missingPictureIsReported() {
        assertEquals("no picture", applier.apply(Slot.QUICK))
    }

    @Test
    fun damagedPictureIsReported() {
        File(directory, "quick.jpg").writeText("not a picture")
        assertEquals("damaged picture", applier.apply(Slot.QUICK))
    }
}
