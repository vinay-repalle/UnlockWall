package io.github.vinayrepalle.unlockwall.spike

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import java.io.File
import java.util.concurrent.Executors
import kotlin.random.Random

/** E5/E6: switches between two cached test images ("A" and "B") on unlock or screen off. */
object WallpaperProbe {
    const val OFF = "off"
    const val HOME = "home"
    const val LOCK = "lock"
    const val BOTH = "both"
    const val AT_UNLOCK = "unlock"
    const val AT_SCREEN_OFF = "screen-off"

    private val worker = Executors.newSingleThreadExecutor()

    fun mode(context: Context): String = EventLog.prefs(context).getString("wallpaper", OFF)!!

    fun setMode(context: Context, mode: String) {
        EventLog.prefs(context).edit().putString("wallpaper", mode).apply()
        EventLog.log(context, "WALLPAPER_MODE", "mode=$mode")
        if (mode != OFF) worker.execute { prepare(context) }
    }

    fun time(context: Context): String =
        EventLog.prefs(context).getString("wallpaperWhen", AT_UNLOCK)!!

    fun setTime(context: Context, time: String) {
        EventLog.prefs(context).edit().putString("wallpaperWhen", time).apply()
        EventLog.log(context, "WALLPAPER_WHEN", "when=$time")
    }

    /** Called for both possible moments; only the chosen one changes the wallpaper. */
    fun onMoment(context: Context, moment: String) {
        val mode = mode(context)
        if (mode == OFF || time(context) != moment || FriendScenario.enabled(context)) return
        worker.execute { apply(context, mode, moment) }
    }

    private fun apply(context: Context, mode: String, moment: String) {
        try {
            val manager = WallpaperManager.getInstance(context)
            if (!manager.isWallpaperSupported || !manager.isSetWallpaperAllowed) {
                EventLog.log(
                    context,
                    "WALLPAPER_BLOCKED",
                    "supported=${manager.isWallpaperSupported} allowed=${manager.isSetWallpaperAllowed}",
                )
                return
            }
            prepare(context)
            val prefs = EventLog.prefs(context)
            val name = if (prefs.getString("lastImage", "B") == "A") "B" else "A"
            val which = when (mode) {
                HOME -> WallpaperManager.FLAG_SYSTEM
                LOCK -> WallpaperManager.FLAG_LOCK
                else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            }
            // Two methods, two changes each (so both get image A and B), to compare speed:
            // "stream" hands over the JPEG file as it is; "bitmap" decodes it first.
            val count = prefs.getInt("wallpaperCount", 0)
            val method = if (count / 2 % 2 == 0) "stream" else "bitmap"
            val file = image(context, name)
            val start = SystemClock.elapsedRealtime()
            val decoded: Long
            val id = if (method == "stream") {
                decoded = start
                file.inputStream().use { manager.setStream(it, null, false, which) }
            } else {
                val bitmap = BitmapFactory.decodeFile(file.path)
                decoded = SystemClock.elapsedRealtime()
                manager.setBitmap(bitmap, null, false, which)
            }
            val done = SystemClock.elapsedRealtime()
            prefs.edit().putString("lastImage", name).putInt("wallpaperCount", count + 1).apply()
            EventLog.log(
                context,
                "WALLPAPER_SET",
                "image=$name method=$method mode=$mode when=$moment resultId=$id decodeMs=${decoded - start} setMs=${done - decoded}",
            )
        } catch (e: Exception) {
            EventLog.log(context, "WALLPAPER_ERROR", e.toString())
        }
    }

    private fun image(context: Context, name: String) = File(context.filesDir, "test-$name.jpg")

    /** Draws screen-sized test images once. Random shapes make decoding less trivial. */
    private fun prepare(context: Context) {
        val mode = context.getSystemService(DisplayManager::class.java)
            .getDisplay(Display.DEFAULT_DISPLAY).mode
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        for ((name, colors) in listOf(
            "A" to (Color.rgb(190, 30, 30) to Color.rgb(60, 0, 0)),
            "B" to (Color.rgb(110, 110, 110) to Color.rgb(25, 25, 25)),
        )) {
            val target = image(context, name)
            if (target.exists()) continue
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(), colors.first, colors.second, Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
            val random = Random(name.hashCode())
            repeat(600) {
                val shade = random.nextInt(256)
                paint.color = Color.argb(40, shade, shade, shade)
                canvas.drawCircle(
                    random.nextInt(width).toFloat(),
                    random.nextInt(height).toFloat(),
                    10f + random.nextInt(60),
                    paint,
                )
            }
            paint.color = Color.WHITE
            paint.textSize = width * 0.6f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(name, width / 2f, height / 2f, paint)
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            bitmap.recycle()
        }
    }
}
