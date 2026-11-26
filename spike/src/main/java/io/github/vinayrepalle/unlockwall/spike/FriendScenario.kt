package io.github.vinayrepalle.unlockwall.spike

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.hardware.display.DisplayManager
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Three home screen wallpapers. NORMAL is the safe default: it comes back at every screen
 * off and at shutdown, and stays whenever the unlock is unclear or the app has only just
 * started. QUICK and SLOW are applied after unlock.
 */
object FriendScenario {
    private const val NORMAL = "normal"
    private const val QUICK = "quick"
    private const val SLOW = "slow"
    private const val QUICK_LIMIT_MS = 1_000

    /** After a start, unlock signals can be late or out of order (seen after reboot). */
    private const val HOLD_AFTER_START_MS = 60_000L

    private val worker = Executors.newSingleThreadExecutor()
    private var holdUntil = 0L

    @Volatile
    private var shuttingDown = false

    fun enabled(context: Context): Boolean = EventLog.prefs(context).getBoolean("friend", false)

    fun setEnabled(context: Context, on: Boolean) {
        EventLog.prefs(context).edit().putBoolean("friend", on).remove("friendShown").apply()
        EventLog.log(context, "FRIEND_MODE", "on=$on")
        if (on) show(context, NORMAL, "switched-on", force = true)
    }

    fun reset(context: Context, why: String) {
        if (enabled(context)) show(context, NORMAL, why)
    }

    /** Restores NORMAL, whatever was recorded, so a stopped app does not leave a photo. */
    fun onServiceStart(context: Context) {
        shuttingDown = false
        holdUntil = SystemClock.elapsedRealtime() + HOLD_AFTER_START_MS
        if (enabled(context)) show(context, NORMAL, "service-start", force = true)
    }

    /** Waits for the change to finish, so the phone does not power off in the middle. */
    fun onShutdown(context: Context) {
        shuttingDown = true
        if (!enabled(context)) return
        try {
            show(context, NORMAL, "shutdown", force = true).get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            EventLog.log(context, "FRIEND_ERROR", "shutdown wait $e")
        }
    }

    /** [sinceScreenOnMs] is null when no SCREEN_ON was seen: unclear, so nothing changes. */
    fun onUnlock(context: Context, sinceScreenOnMs: Long?) {
        if (!enabled(context)) return
        if (SystemClock.elapsedRealtime() < holdUntil) {
            EventLog.log(context, "FRIEND_HOLD", "just-started kept=normal")
            show(context, NORMAL, "hold")
            return
        }
        if (sinceScreenOnMs == null) {
            EventLog.log(context, "FRIEND_UNCLEAR", "kept=normal")
            return
        }
        val kind = if (sinceScreenOnMs < QUICK_LIMIT_MS) QUICK else SLOW
        show(context, kind, "unlock sinceScreenOnMs=$sinceScreenOnMs")
    }

    private fun show(context: Context, kind: String, why: String, force: Boolean = false): Future<*> {
        // Keeps the CPU awake until the change is done, even though the screen is going off.
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "uwspike:wallpaper")
        wakeLock.acquire(10_000)
        return worker.submit {
            try {
                val prefs = EventLog.prefs(context)
                if (shuttingDown && kind != NORMAL) return@submit
                if (!force && prefs.getString("friendShown", null) == kind) return@submit
                // Forget the record first: if the phone dies mid-change, nothing is assumed.
                prefs.edit().remove("friendShown").commit()
                val file = image(context, kind)
                val start = SystemClock.elapsedRealtime()
                file.inputStream().use {
                    WallpaperManager.getInstance(context)
                        .setStream(it, null, false, WallpaperManager.FLAG_SYSTEM)
                }
                val setMs = SystemClock.elapsedRealtime() - start
                prefs.edit().putString("friendShown", kind).commit()
                EventLog.log(context, "FRIEND_SET", "kind=$kind setMs=$setMs why=$why")
            } catch (e: Exception) {
                EventLog.log(context, "FRIEND_ERROR", "kind=$kind $e")
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    /** Screen-sized test picture with a large word on it, drawn once. */
    private fun image(context: Context, kind: String): File {
        val target = File(context.filesDir, "friend-$kind.jpg")
        if (target.exists()) return target
        val mode = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY).mode
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        val (background, word, note) = when (kind) {
            QUICK -> Triple(Color.rgb(170, 20, 30), "PERSONAL", "quick unlock (finger)")
            SLOW -> Triple(Color.rgb(185, 90, 0), "SLOW", "slow unlock (PIN)")
            else -> Triple(Color.rgb(30, 110, 60), "NORMAL", "default")
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = width * 0.17f
        canvas.drawText(word, width / 2f, height / 2f, paint)
        paint.textSize = width * 0.07f
        canvas.drawText(note, width / 2f, height / 2f + width * 0.14f, paint)
        target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return target
    }
}
