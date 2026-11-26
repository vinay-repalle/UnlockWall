package io.github.vinayrepalle.unlockwall.spike

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val enabled = EventLog.prefs(context).getBoolean("enabled", false)
        EventLog.log(context, "BOOT_COMPLETED", "enabled=$enabled ${EventLog.state(context)}")
        if (enabled) context.startForegroundService(Intent(context, SpikeService::class.java))
    }
}
