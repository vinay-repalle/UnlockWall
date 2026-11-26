package io.github.vinayrepalle.unlockwall.spike

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import java.time.Instant
import java.time.ZoneId

class SpikeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EventLog.log(
            this,
            "PROCESS_START",
            "device=${Build.MANUFACTURER} ${Build.MODEL} android=${Build.VERSION.RELEASE} " +
                "sdk=${Build.VERSION.SDK_INT} build=${Build.DISPLAY}",
        )
        logPreviousExits()
    }

    /** Why the system says earlier processes of this app died (needed for E4). */
    private fun logPreviousExits() {
        val prefs = EventLog.prefs(this)
        val seen = prefs.getLong("lastExit", 0)
        val exits = getSystemService(ActivityManager::class.java)
            .getHistoricalProcessExitReasons(null, 0, 5)
            .filter { it.timestamp > seen }
            .sortedBy { it.timestamp }
        for (exit in exits) {
            val at = Instant.ofEpochMilli(exit.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
            EventLog.log(
                this,
                "PREVIOUS_EXIT",
                "at=$at oldPid=${exit.pid} reason=${exit.reason} status=${exit.status} " +
                    "importance=${exit.importance} description=${exit.description}",
            )
        }
        exits.lastOrNull()?.let { prefs.edit().putLong("lastExit", it.timestamp).apply() }
    }
}
