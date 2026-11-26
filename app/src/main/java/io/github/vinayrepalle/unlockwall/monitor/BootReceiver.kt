package io.github.vinayrepalle.unlockwall.monitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.vinayrepalle.unlockwall.UnlockWallApp
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking

/** Restarts the service after a reboot or an app update, if the switch is on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val app = context.applicationContext as UnlockWallApp
        val pending = goAsync()
        thread {
            try {
                val enabled = runBlocking { app.settings.current().enabled }
                app.diagnostics.log("${action.substringAfterLast('.')} enabled=$enabled")
                if (enabled) UnlockMonitorService.start(app)
            } catch (e: Exception) {
                app.diagnostics.log("ERROR restart $e")
            } finally {
                pending.finish()
            }
        }
    }
}
