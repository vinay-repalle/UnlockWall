package io.github.vinayrepalle.unlockwall

import android.app.Application
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.view.Display
import androidx.datastore.preferences.preferencesDataStore
import io.github.vinayrepalle.unlockwall.diagnostics.Diagnostics
import io.github.vinayrepalle.unlockwall.monitor.WallpaperController
import io.github.vinayrepalle.unlockwall.settings.SettingsRepository
import io.github.vinayrepalle.unlockwall.wallpaper.WallpaperApplier
import io.github.vinayrepalle.unlockwall.wallpaper.WallpaperCache
import java.io.File

private val Context.settingsStore by preferencesDataStore("settings")

/** Holds the one instance of everything that the screen and the service share. */
class UnlockWallApp : Application() {

    val settings by lazy { SettingsRepository(settingsStore) }

    val diagnostics by lazy { Diagnostics(File(filesDir, "diagnostics.log")) }

    val cache by lazy {
        val mode = getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY).mode
        WallpaperCache(File(filesDir, "wallpapers"), mode.physicalWidth, mode.physicalHeight)
    }

    val controller by lazy {
        val applier = WallpaperApplier(this, cache)
        val power = getSystemService(PowerManager::class.java)
        WallpaperController(
            settings = settings,
            available = cache::available,
            apply = applier::apply,
            log = diagnostics::log,
            // Keeps the CPU on until the change is done, even as the screen goes off.
            keepAwake = {
                val lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "unlockwall:wallpaper")
                lock.acquire(10_000)
                return@WallpaperController { if (lock.isHeld) lock.release() }
            },
        )
    }
}
