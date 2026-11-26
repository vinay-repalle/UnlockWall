package io.github.vinayrepalle.unlockwall.monitor

import io.github.vinayrepalle.unlockwall.core.Request
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.core.WallpaperPolicy
import io.github.vinayrepalle.unlockwall.settings.SettingsRepository
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlinx.coroutines.runBlocking

/**
 * Carries out requests one at a time, in order, off the main thread. No Android imports.
 *
 * @param available slots that have a picture.
 * @param apply sets the slot's picture; returns null on success, otherwise what went wrong.
 * @param keepAwake called before queueing; returns the function that releases it again.
 */
class WallpaperController(
    private val settings: SettingsRepository,
    private val available: () -> Set<Slot>,
    private val apply: (Slot) -> String?,
    private val log: (String) -> Unit,
    private val keepAwake: () -> () -> Unit = { {} },
) {
    private val worker = Executors.newSingleThreadExecutor()

    fun submit(request: Request): Future<*> {
        val release = keepAwake()
        return worker.submit {
            try {
                handle(request)
            } catch (e: Exception) {
                // Fail safe: leave the wallpaper as it is.
                log("ERROR ${request.reason} $e")
            } finally {
                release()
            }
        }
    }

    private fun handle(request: Request) = runBlocking {
        val current = settings.current()
        val target = WallpaperPolicy.decide(request, current.enabled, available(), current.showing)
        if (target == null) {
            log("KEEP reason=${request.reason} showing=${current.showing} enabled=${current.enabled}")
            return@runBlocking
        }
        // Forget first: if we die midway, the next request does not trust an old record.
        settings.setShowing(null)
        val started = System.nanoTime()
        val problem = apply(target)
        val tookMs = (System.nanoTime() - started) / 1_000_000
        if (problem == null) {
            settings.setShowing(target)
            log("SHOW $target reason=${request.reason} tookMs=$tookMs")
        } else {
            log("FAILED $target reason=${request.reason} $problem")
        }
    }
}
