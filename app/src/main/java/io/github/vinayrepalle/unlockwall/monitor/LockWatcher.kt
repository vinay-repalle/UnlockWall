package io.github.vinayrepalle.unlockwall.monitor

import android.app.KeyguardManager
import android.os.Handler
import android.os.SystemClock

/**
 * Asks "still locked?" every 100 ms while the lock screen is on, because no broadcast says
 * when face unlock succeeds (finding F17). Stops by itself. Main thread only.
 */
class LockWatcher(
    private val keyguard: KeyguardManager,
    private val handler: Handler,
    private val onDeviceUnlocked: (atMs: Long) -> Unit,
) {
    private var deadline = 0L

    private val check = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            when {
                !keyguard.isDeviceLocked -> onDeviceUnlocked(now)
                now < deadline -> handler.postDelayed(this, INTERVAL_MS)
            }
        }
    }

    fun start() {
        stop()
        deadline = SystemClock.elapsedRealtime() + LIMIT_MS
        handler.postDelayed(check, INTERVAL_MS)
    }

    fun stop() = handler.removeCallbacks(check)

    private companion object {
        const val INTERVAL_MS = 100L

        /** A lock screen left on longer than this is judged by USER_PRESENT instead. */
        const val LIMIT_MS = 60_000L
    }
}
