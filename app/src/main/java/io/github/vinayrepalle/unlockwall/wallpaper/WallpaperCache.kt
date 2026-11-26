package io.github.vinayrepalle.unlockwall.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import io.github.vinayrepalle.unlockwall.core.Slot
import java.io.File
import java.io.IOException

/**
 * Keeps, per slot, in app-private storage: the screen-sized JPEG that is set as wallpaper
 * (so setting it needs no work at unlock time), a copy of the whole photo and how it was
 * placed (so it can be adjusted again). The app still works after the original photo is
 * deleted from the gallery.
 *
 * Blocking: call off the main thread.
 */
class WallpaperCache(
    private val directory: File,
    val width: Int,
    val height: Int,
) {
    private fun target(slot: Slot) = File(directory, "${slot.name.lowercase()}.jpg")
    private fun photo(slot: Slot) = File(directory, "${slot.name.lowercase()}.photo.jpg")
    private fun placement(slot: Slot) = File(directory, "${slot.name.lowercase()}.placement")

    /** The cached picture, or null if the slot has none. */
    fun file(slot: Slot): File? = target(slot).takeIf { it.isFile && it.length() > 0 }

    fun available(): Set<Slot> = Slot.entries.filter { file(it) != null }.toSet()

    fun remove(slot: Slot) {
        target(slot).delete()
        photo(slot).delete()
        placement(slot).delete()
    }

    /**
     * The picture to show when adjusting a saved slot again, with its placement. A slot
     * saved before photos were kept only has the cropped picture. Null if empty.
     */
    fun editable(slot: Slot): Pair<File, Adjustment>? {
        val wallpaper = file(slot) ?: return null
        val whole = photo(slot).takeIf { it.isFile && it.length() > 0 }
            ?: return wallpaper to Adjustment()
        val saved = runCatching { placement(slot).readText() }.getOrDefault("")
        return whole to Adjustment.decode(saved)
    }

    /**
     * Stores [picture], placed as in [adjustment], at screen size.
     * On failure returns false and keeps whatever the slot had before.
     *
     * @param newPhoto false when [picture] is the slot's own kept photo, which then is not
     * compressed a second time.
     */
    fun save(
        slot: Slot,
        picture: Bitmap,
        adjustment: Adjustment = Adjustment(),
        newPhoto: Boolean = true,
    ): Boolean {
        // Drawing a recycled bitmap kills the process instead of throwing.
        if (picture.isRecycled) return false
        val temporary = File(directory, "${slot.name.lowercase()}.tmp")
        val temporaryPhoto = File(directory, "${slot.name.lowercase()}.photo.tmp")
        val keepPhoto = newPhoto || !photo(slot).isFile
        return try {
            val screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Pictures.draw(Canvas(screen), picture, adjustment, width.toFloat(), height.toFloat())
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("no $directory")
            write(screen, 92, temporary)
            if (keepPhoto) write(picture, 95, temporaryPhoto)
            // A rename swaps a whole file at once: never a half written picture.
            if (keepPhoto && !temporaryPhoto.renameTo(photo(slot))) throw IOException("rename")
            placement(slot).writeText(adjustment.encode())
            temporary.renameTo(target(slot))
        } catch (e: IOException) {
            false
        } catch (e: RuntimeException) {
            false
        } catch (e: OutOfMemoryError) {
            false
        } finally {
            temporary.delete()
            temporaryPhoto.delete()
        }
    }

    private fun write(bitmap: Bitmap, quality: Int, to: File) {
        to.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it)) throw IOException("compress")
        }
    }
}
