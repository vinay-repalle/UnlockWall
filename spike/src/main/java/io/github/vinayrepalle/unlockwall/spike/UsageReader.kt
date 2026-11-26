package io.github.vinayrepalle.unlockwall.spike

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * Second unlock signal. Only keyguard shown/hidden events are logged;
 * everything else (including which apps were used) is skipped.
 */
object UsageReader {
    fun hasAccess(context: Context): Boolean =
        context.getSystemService(AppOpsManager::class.java).unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED

    fun read(context: Context) {
        if (!hasAccess(context)) return
        val prefs = EventLog.prefs(context)
        val now = System.currentTimeMillis()
        val from = prefs.getLong("lastUsage", now - 60_000)
        val events = context.getSystemService(UsageStatsManager::class.java).queryEvents(from, now)
            ?: return
        val event = UsageEvents.Event()
        var next = from
        while (events.getNextEvent(event)) {
            val name = when (event.eventType) {
                UsageEvents.Event.KEYGUARD_SHOWN -> "USAGE_KEYGUARD_SHOWN"
                UsageEvents.Event.KEYGUARD_HIDDEN -> "USAGE_KEYGUARD_HIDDEN"
                else -> continue
            }
            EventLog.log(context, name, "eventWallMs=${event.timeStamp} readDelayMs=${now - event.timeStamp}")
            next = maxOf(next, event.timeStamp + 1)
        }
        prefs.edit().putLong("lastUsage", next).apply()
    }
}
