package io.github.vinayrepalle.unlockwall.spike

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.util.Locale

/** Foreground service that only keeps the screen/unlock receiver alive and logs. */
class SpikeService : Service() {

    private var screenOnNanos: Long? = null
    private var screenOffNanos: Long? = null
    private val main = Handler(Looper.getMainLooper())
    private val poller by lazy { LockPoller(this, main) }
    private val usagePoller by lazy { UsagePoller(applicationContext) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val received = SystemClock.elapsedRealtimeNanos()
            val event = intent.action!!.substringAfterLast('.')
            val state = EventLog.state(context)
            val details = "$state sinceScreenOnMs=${msBetween(screenOnNanos, received)} " +
                "sinceScreenOffMs=${msBetween(screenOffNanos, received)}"

            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    screenOnNanos = received
                    if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
                        poller.start(received)
                        usagePoller.start(received)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    poller.stop("screen-off")
                    usagePoller.stop()
                    FriendScenario.reset(applicationContext, "screen-off")
                    WallpaperProbe.onMoment(applicationContext, WallpaperProbe.AT_SCREEN_OFF)
                    screenOffNanos = received
                    screenOnNanos = null
                    // Also read here, so usage events show up even if USER_PRESENT is missed.
                    EventLog.io.execute { UsageReader.read(applicationContext) }
                }
                Intent.ACTION_USER_PRESENT -> {
                    FriendScenario.onUnlock(
                        applicationContext,
                        screenOnNanos?.let { (received - it) / 1_000_000 },
                    )
                    WallpaperProbe.onMoment(applicationContext, WallpaperProbe.AT_UNLOCK)
                    // Usage events are written with a delay, so read them a little later.
                    main.postDelayed({
                        EventLog.io.execute { UsageReader.read(applicationContext) }
                    }, 2_000)
                }
            }

            val costMs = msBetween(received, SystemClock.elapsedRealtimeNanos())
            EventLog.log(context, event, "$details onReceiveMs=$costMs")
        }
    }

    private fun msBetween(from: Long?, to: Long): String =
        if (from == null) "-" else String.format(Locale.US, "%.1f", (to - from) / 1_000_000.0)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Experiment logger", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setColor(getColor(R.color.ink))
            .setContentTitle("UW Spike is logging")
            .setOngoing(true)
            .build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Must be EXPORTED: USER_PRESENT is sent by SystemUI, which does not run as the system
        // user, so a NOT_EXPORTED receiver never gets it (seen on the iQOO). All three actions
        // are protected, so other apps cannot fake them.
        registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        running = true
        EventLog.log(this, "SERVICE_CREATE", EventLog.state(this))
        FriendScenario.reset(applicationContext, "service-start")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // A null intent means the system restarted the service after killing it.
        EventLog.log(this, "SERVICE_START", "restartedBySystem=${intent == null}")
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        EventLog.log(this, "TASK_REMOVED")
    }

    override fun onDestroy() {
        unregisterReceiver(receiver)
        poller.stop("service-destroyed")
        usagePoller.stop()
        FriendScenario.reset(applicationContext, "service-stop")
        running = false
        EventLog.log(this, "SERVICE_DESTROY")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "logger"

        /** True while the service exists in this process. */
        var running = false
            private set
    }
}
