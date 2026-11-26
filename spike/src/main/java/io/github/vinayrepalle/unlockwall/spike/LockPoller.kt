package io.github.vinayrepalle.unlockwall.spike

import android.app.KeyguardManager
import android.content.Context
import android.os.Handler
import android.os.SystemClock
import java.util.Locale

/**
 * Asks "still locked?" every 100 ms while the screen is on and locked, to see whether the
 * answer changes before USER_PRESENT arrives. Used from the main thread only.
 */
class LockPoller(private val context: Context, private val handler: Handler) {
    private val keyguard = context.getSystemService(KeyguardManager::class.java)

    private var running = false
    private var startNanos = 0L
    private var checks = 0
    private var checkingNanos = 0L
    private var deviceUnlockLogged = false

    private val tick = object : Runnable {
        override fun run() {
            val before = SystemClock.elapsedRealtimeNanos()
            val keyguardLocked = keyguard.isKeyguardLocked
            val deviceLocked = keyguard.isDeviceLocked
            val now = SystemClock.elapsedRealtimeNanos()
            checks++
            checkingNanos += now - before

            if (!deviceLocked && !deviceUnlockLogged) {
                deviceUnlockLogged = true
                EventLog.log(context, "POLL_DEVICE_UNLOCKED", "sinceScreenOnMs=${ms(now - startNanos)}")
            }
            when {
                !keyguardLocked -> {
                    EventLog.log(context, "POLL_KEYGUARD_GONE", "sinceScreenOnMs=${ms(now - startNanos)}")
                    stop("unlocked")
                }
                now - startNanos > LIMIT_NANOS -> stop("time-limit")
                else -> handler.postDelayed(this, INTERVAL_MS)
            }
        }
    }

    fun start(screenOnNanos: Long) {
        stop("restarted")
        running = true
        startNanos = screenOnNanos
        checks = 0
        checkingNanos = 0
        deviceUnlockLogged = false
        handler.postDelayed(tick, INTERVAL_MS)
    }

    fun stop(why: String) {
        if (!running) return
        running = false
        handler.removeCallbacks(tick)
        EventLog.log(context, "POLL_END", "why=$why checks=$checks checkingMs=${ms(checkingNanos)}")
    }

    private fun ms(nanos: Long) = String.format(Locale.US, "%.1f", nanos / 1_000_000.0)

    private companion object {
        const val INTERVAL_MS = 100L
        const val LIMIT_NANOS = 60_000_000_000L
    }
}
