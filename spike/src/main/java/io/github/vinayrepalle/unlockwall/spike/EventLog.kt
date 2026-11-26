package io.github.vinayrepalle.unlockwall.spike

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Display
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

/**
 * One tab-separated line per event:
 * wall time, elapsed ms since boot, pid, test label, event, details.
 */
object EventLog {
    private const val TAG = "UWSpike"
    private val timeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /** Single background thread: file writes and slow probes never run on the main thread. */
    val io = Executors.newSingleThreadExecutor()

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences("spike", Context.MODE_PRIVATE)

    fun file(context: Context) = File(context.filesDir, "events.log")

    fun label(context: Context): String = prefs(context).getString("label", "none")!!

    /** Lock and screen state at this instant, as the app can see it. */
    fun state(context: Context): String {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val display = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY)
        val displayState = when (display?.state) {
            Display.STATE_ON -> "ON"
            Display.STATE_OFF -> "OFF"
            Display.STATE_DOZE -> "DOZE"
            Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
            else -> "OTHER(${display?.state})"
        }
        return "kgLocked=${keyguard.isKeyguardLocked} devLocked=${keyguard.isDeviceLocked} " +
            "interactive=${power.isInteractive} display=$displayState"
    }

    fun log(context: Context, event: String, details: String = "") {
        val elapsedMs = SystemClock.elapsedRealtimeNanos() / 1_000_000.0
        val line = listOf(
            LocalDateTime.now().format(timeFormat),
            String.format(Locale.US, "%.3f", elapsedMs),
            Process.myPid().toString(),
            label(context),
            event,
            details,
        ).joinToString("\t")
        Log.i(TAG, line)
        val target = file(context)
        io.execute { target.appendText(line + "\n") }
    }
}
