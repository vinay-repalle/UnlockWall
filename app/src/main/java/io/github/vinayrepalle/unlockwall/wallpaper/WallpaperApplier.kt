package io.github.vinayrepalle.unlockwall.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import io.github.vinayrepalle.unlockwall.core.Slot

/** Sets a cached picture as the home screen wallpaper. Blocking. */
class WallpaperApplier(private val context: Context, private val cache: WallpaperCache) {

    /** Returns null on success, otherwise a short reason. The old wallpaper then stays. */
    fun apply(slot: Slot): String? {
        val manager = WallpaperManager.getInstance(context)
        if (!manager.isWallpaperSupported) return "wallpaper not supported"
        if (!manager.isSetWallpaperAllowed) return "not allowed on this phone"
        val file = cache.file(slot) ?: return "no picture"
        // Reads only the header: a damaged file must not reach the system.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "damaged picture"
        return try {
            file.inputStream().use {
                manager.setStream(it, null, false, WallpaperManager.FLAG_SYSTEM)
            }
            null
        } catch (e: Exception) {
            e.toString()
        }
    }
}
