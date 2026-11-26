package io.github.vinayrepalle.unlockwall.wallpaper

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Keeps one screen-sized JPEG per slot in app-private storage, so the app still works
 * after the original photo is deleted, and setting it needs no work at unlock time.
 *
 * Blocking: call off the main thread.
 */
class WallpaperCache(
    private val directory: File,
    private val width: Int,
    private val height: Int,
) {
    private fun target(slot: Slot) = File(directory, "${slot.name.lowercase()}.jpg")

    /** The cached picture, or null if the slot has none. */
    fun file(slot: Slot): File? = target(slot).takeIf { it.isFile && it.length() > 0 }

    fun available(): Set<Slot> = Slot.entries.filter { file(it) != null }.toSet()

    fun remove(slot: Slot) {
        target(slot).delete()
    }

    /**
     * Scales the picture to cover the screen, crops the middle and stores it.
     * On failure returns false and keeps whatever the slot had before.
     */
    fun save(slot: Slot, source: ImageDecoder.Source): Boolean {
        val temporary = File(directory, "${slot.name.lowercase()}.tmp")
        return try {
            val bitmap = decodeToCover(source)
            val cropped = Bitmap.createBitmap(
                bitmap,
                (bitmap.width - width) / 2,
                (bitmap.height - height) / 2,
                width,
                height,
            )
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("no $directory")
            temporary.outputStream().use {
                if (!cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)) throw IOException("compress")
            }
            // A rename swaps the whole file at once: never a half written picture.
            temporary.renameTo(target(slot))
        } catch (e: IOException) {
            false
        } catch (e: RuntimeException) {
            false
        } catch (e: OutOfMemoryError) {
            false
        } finally {
            temporary.delete()
        }
    }

    /** Decodes at the smallest size that still covers the screen in both directions. */
    private fun decodeToCover(source: ImageDecoder.Source): Bitmap =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = max(
                width.toFloat() / info.size.width,
                height.toFloat() / info.size.height,
            )
            decoder.setTargetSize(
                max(width, (info.size.width * scale).roundToInt()),
                max(height, (info.size.height * scale).roundToInt()),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
}
