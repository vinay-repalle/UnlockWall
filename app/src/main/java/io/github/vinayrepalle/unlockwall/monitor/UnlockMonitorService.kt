package io.github.vinayrepalle.unlockwall.monitor

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import io.github.vinayrepalle.unlockwall.MainActivity
import io.github.vinayrepalle.unlockwall.R
import io.github.vinayrepalle.unlockwall.UnlockWallApp
import io.github.vinayrepalle.unlockwall.core.PhoneEvent
import io.github.vinayrepalle.unlockwall.core.Reason
import io.github.vinayrepalle.unlockwall.core.Request
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.core.UnlockStateMachine
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Stays alive to hear screen and unlock broadcasts. It only observes: nothing here can
 * delay or change the unlock itself.
 */
class UnlockMonitorService : Service() {

    private val app get() = application as UnlockWallApp
    private val machine = UnlockStateMachine()
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val main = Handler(Looper.getMainLooper())
    private var screenOnAtMs: Long? = null
    private var screenOffAtMs: Long? = null

    private val checkLockScreen = Runnable {
        val showing = keyguard.isKeyguardLocked
        note("LOCK_SCREEN_CHECK stillShowing=$showing")
        send(PhoneEvent.LockScreenCheck(showing))
    }

    // Log only, to learn about wakes where the phone is unlocked already at SCREEN_ON.
    private val checkAfterScreenOff = Runnable {
        val since = screenOffAtMs?.let { SystemClock.elapsedRealtime() - it }
        note("AFTER_SCREEN_OFF afterMs=$since deviceLocked=${keyguard.isDeviceLocked}")
    }

    private val watcher by lazy {
        LockWatcher(keyguard, main) { atMs ->
            val showing = keyguard.isKeyguardLocked
            note("DEVICE_UNLOCKED${sinceScreenOn(atMs)} lockScreenShowing=$showing")
            send(PhoneEvent.DeviceUnlocked(atMs, showing))
            if (showing) main.postDelayed(checkLockScreen, UnlockStateMachine.LOCK_SCREEN_CHECK_MS)
        }
    }

    private fun stopChecks() {
        watcher.stop()
        main.removeCallbacks(checkLockScreen)
        main.removeCallbacks(checkAfterScreenOff)
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val now = SystemClock.elapsedRealtime()
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    val locked = keyguard.isKeyguardLocked
                    val deviceLocked = keyguard.isDeviceLocked
                    screenOnAtMs = now
                    main.removeCallbacks(checkAfterScreenOff)
                    val offFor = screenOffAtMs?.let { " offForMs=${now - it}" } ?: ""
                    note("SCREEN_ON keyguardLocked=$locked deviceLocked=$deviceLocked$offFor")
                    send(PhoneEvent.ScreenOn(now, locked, deviceLocked))
                    if (locked && deviceLocked) watcher.start()
                }

                Intent.ACTION_SCREEN_OFF -> {
                    stopChecks()
                    screenOnAtMs = null
                    screenOffAtMs = now
                    val deviceLocked = keyguard.isDeviceLocked
                    note("SCREEN_OFF deviceLocked=$deviceLocked")
                    send(PhoneEvent.ScreenOff(deviceLocked))
                    main.postDelayed(checkAfterScreenOff, 500)
                }

                Intent.ACTION_USER_PRESENT -> {
                    val stillLocked = keyguard.isKeyguardLocked
                    if (!stillLocked) watcher.stop()
                    note("USER_PRESENT${sinceScreenOn(now)} stillLocked=$stillLocked")
                    send(PhoneEvent.UserPresent(now, stillLocked))
                }

                Intent.ACTION_SHUTDOWN -> {
                    stopChecks()
                    note("SHUTDOWN")
                    // Wait, so the phone does not power off halfway through the change.
                    runCatching { send(PhoneEvent.ShuttingDown)?.get(5, TimeUnit.SECONDS) }
                    app.diagnostics.flush()
                }
            }
        }
    }

    private fun note(message: String) = app.diagnostics.log(message)

    private fun sinceScreenOn(now: Long) = screenOnAtMs?.let { " afterMs=${now - it}" } ?: ""

    private fun send(event: PhoneEvent): Future<*>? =
        machine.onEvent(event)?.let(app.controller::submit)

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        // EXPORTED because SystemUI, not the system user, sends USER_PRESENT (finding F1).
        // All four actions are protected: other apps cannot send them.
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SHUTDOWN)
        }
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        running = true
        note("SERVICE_STARTED")
        send(PhoneEvent.ServiceStarted(SystemClock.elapsedRealtime()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SWITCH_OFF -> {
                note("SWITCHED_OFF")
                stopChecks()
                send(PhoneEvent.SwitchedOff)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_PICTURES_CHANGED ->
                app.controller.submit(Request(Slot.NORMAL, Reason.PICTURES_CHANGED, force = true))
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        stopChecks()
        running = false
        note("SERVICE_STOPPED")
        // Ignored by the policy when the switch is off (already restored then).
        send(PhoneEvent.ServiceStopping)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title))
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL = "monitor"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_SWITCH_OFF = "io.github.vinayrepalle.unlockwall.SWITCH_OFF"
        private const val ACTION_PICTURES_CHANGED = "io.github.vinayrepalle.unlockwall.PICTURES"

        /** True while the service exists in this process. */
        @Volatile
        var running = false
            private set

        fun start(context: Context) {
            context.startForegroundService(Intent(context, UnlockMonitorService::class.java))
        }

        /** Call after saving the switch as off: restores Normal, then stops. */
        fun switchOff(context: Context) {
            context.startForegroundService(
                Intent(context, UnlockMonitorService::class.java).setAction(ACTION_SWITCH_OFF),
            )
        }

        fun picturesChanged(context: Context) {
            context.startForegroundService(
                Intent(context, UnlockMonitorService::class.java).setAction(ACTION_PICTURES_CHANGED),
            )
        }
    }
}
