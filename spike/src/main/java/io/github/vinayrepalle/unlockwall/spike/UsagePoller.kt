package io.github.vinayrepalle.unlockwall.spike

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import java.util.Locale

/**
 * Asks usage access every 100 ms, while the screen is on and locked, whether the keyguard
 * has gone. Record-only: it logs when the app can first see this, on the app's own clock,
 * to compare with USER_PRESENT.
 */
class UsagePoller(private val context: Context) {
    private val handler = Handler(HandlerThread("usage-poll").apply { start() }.looper)
    private val manager = context.getSystemService(UsageStatsManager::class.java)

    fun start(screenOnNanos: Long) {
        stop()
        if (!UsageReader.hasAccess(context)) return
        handler.post(object : Runnable {
            override fun run() {
                val sinceOnMs = (SystemClock.elapsedRealtimeNanos() - screenOnNanos) / 1_000_000.0
                val hiddenAt = latestHidden()
                if (hiddenAt != null) {
                    EventLog.log(
                        context,
                        "POLL_USAGE_HIDDEN",
                        String.format(
                            Locale.US,
                            "sinceScreenOnMs=%.1f stampAgeMs=%d",
                            sinceOnMs,
                            System.currentTimeMillis() - hiddenAt,
                        ),
                    )
                } else if (sinceOnMs < 60_000) {
                    handler.postDelayed(this, 100)
                }
            }
        })
    }

    fun stop() = handler.removeCallbacksAndMessages(null)

    /** Time stamp of the newest keyguard event, only if that event is "hidden". */
    private fun latestHidden(): Long? {
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - 60_000, now + 5_000) ?: return null
        val event = UsageEvents.Event()
        var hidden: Long? = null
        while (events.getNextEvent(event)) {
            when (event.eventType) {
                UsageEvents.Event.KEYGUARD_HIDDEN -> hidden = event.timeStamp
                UsageEvents.Event.KEYGUARD_SHOWN -> hidden = null
            }
        }
        return hidden
    }
}
