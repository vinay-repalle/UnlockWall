package io.github.vinayrepalle.unlockwall.spike

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Plain control screen for the experiments. Not part of the product. */
class MainActivity : Activity() {

    private lateinit var page: LinearLayout
    private lateinit var loggerValue: TextView
    private lateinit var friendValue: TextView
    private lateinit var batteryValue: TextView
    private lateinit var unlocksValue: TextView
    private lateinit var details: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(32))
        }
        page.addView(
            text("UW Spike", 28f, R.color.ink).apply {
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(8), 0, dp(12))
            },
        )
        loggerValue = statusLine("Logger")
        friendValue = statusLine("Friend scenario")
        batteryValue = statusLine("Unrestricted battery")
        unlocksValue = statusLine("Unlocks with this label")

        mainButton("Start logging") {
            EventLog.prefs(this).edit().putBoolean("enabled", true).apply()
            startForegroundService(Intent(this, SpikeService::class.java))
        }
        row("Stop logging") {
            EventLog.prefs(this).edit().putBoolean("enabled", false).apply()
            stopService(Intent(this, SpikeService::class.java))
        }
        row("Allow notification") {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        row("Usage access settings") { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        row("Allow unrestricted battery") {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        }
        row("App settings") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
            )
        }

        heading("Test label")
        chips(
            listOf(
                "none" to "None",
                "pin" to "PIN",
                "pattern" to "Pattern",
                "in-app" to "In-app test",
                "finger-screen-off" to "Finger, screen off",
                "finger-lock-screen" to "Finger, lock screen",
                "wallpaper" to "Wallpaper test",
                "survival" to "Survival test",
            ),
            EventLog.label(this),
        ) { value ->
            EventLog.prefs(this).edit().putString("label", value).apply()
            EventLog.log(this, "LABEL", "label=$value")
        }

        heading("Friend scenario")
        chips(
            listOf("off" to "Off", "on" to "On"),
            if (FriendScenario.enabled(this)) "on" else "off",
        ) { FriendScenario.setEnabled(applicationContext, it == "on") }

        heading("Change wallpaper")
        chips(
            listOf(
                WallpaperProbe.OFF to "Off",
                WallpaperProbe.HOME to "Home screen",
                WallpaperProbe.LOCK to "Lock screen",
                WallpaperProbe.BOTH to "Both",
            ),
            WallpaperProbe.mode(this),
        ) { WallpaperProbe.setMode(applicationContext, it) }

        heading("When to change it")
        chips(
            listOf(
                WallpaperProbe.AT_UNLOCK to "After unlock",
                WallpaperProbe.AT_SCREEN_OFF to "At screen off",
            ),
            WallpaperProbe.time(this),
        ) { WallpaperProbe.setTime(applicationContext, it) }

        heading("In-app prompts")
        row("Show fingerprint prompt") { showPrompt(Authenticators.BIOMETRIC_WEAK) }
        row("Show PIN prompt") { showPrompt(Authenticators.DEVICE_CREDENTIAL) }

        heading("Log")
        row("Save log to Downloads") { saveLog() }
        row("Show or hide details") {
            details.visibility = if (details.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        details = text("", 14f, R.color.muted).apply {
            visibility = View.GONE
            setPadding(0, dp(12), 0, 0)
        }
        page.addView(details)

        val scroll = ScrollView(this).apply { addView(page) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        // The unlock that brought us back may still be on its way into the log.
        page.postDelayed(::refresh, 1_000)
    }

    private fun refresh() {
        val notifications = getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val exempt = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
        val file = EventLog.file(this)
        val counts = if (!file.exists()) {
            emptyMap()
        } else {
            file.readLines()
                .map { it.split('\t') }
                .filter { it.size > 4 && it[4] == "USER_PRESENT" }
                .groupingBy { it[3] }
                .eachCount()
        }
        val running = SpikeService.running
        loggerValue.text = if (running) "\u25CF Running" else "\u25CF Stopped"
        loggerValue.setTextColor(getColor(if (running) R.color.running else R.color.stopped))
        friendValue.text = if (FriendScenario.enabled(this)) "On" else "Off"
        batteryValue.text = if (exempt) "Yes" else "No"
        unlocksValue.text = (counts[EventLog.label(this)] ?: 0).toString()
        details.text = buildString {
            appendLine("Notification allowed: ${yesNo(notifications)}")
            appendLine("Usage access: ${yesNo(UsageReader.hasAccess(this@MainActivity))}")
            appendLine("Unlocks logged per label:")
            if (counts.isEmpty()) append("  none yet")
            else append(counts.entries.joinToString("\n") { "  ${it.key}: ${it.value}" })
        }
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(getColor(color))
    }

    private fun rounded(color: Int) = GradientDrawable().apply {
        setColor(getColor(color))
        cornerRadius = dp(14).toFloat()
    }

    private fun pressable(shape: Drawable?) = RippleDrawable(
        ColorStateList.valueOf(getColor(R.color.ripple)),
        shape,
        if (shape == null) ColorDrawable(Color.WHITE) else null,
    )

    private fun fullWidth(topMargin: Int = 0) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT,
    ).also { it.topMargin = topMargin }

    /** Name on the left, current value on the right. Returns the value view. */
    private fun statusLine(name: String): TextView {
        val line = LinearLayout(this).apply { setPadding(0, dp(5), 0, dp(5)) }
        line.addView(
            text(name, 16f, R.color.muted),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        val value = text("", 16f, R.color.ink)
        line.addView(value)
        page.addView(line, fullWidth())
        return value
    }

    private fun afterTap(action: () -> Unit) {
        action()
        page.postDelayed(::refresh, 500)
    }

    /** The one filled button on the screen. */
    private fun mainButton(label: String, action: () -> Unit) {
        val button = text(label, 17f, R.color.on_ink).apply {
            gravity = Gravity.CENTER
            minHeight = dp(52)
            background = pressable(rounded(R.color.ink))
            isClickable = true
            isFocusable = true
            setOnClickListener { afterTap(action) }
        }
        page.addView(button, fullWidth(dp(16)).also { it.bottomMargin = dp(8) })
    }

    /** A slim tappable line with an arrow and a divider under it. */
    private fun row(label: String, action: () -> Unit) {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
            background = pressable(null)
            isClickable = true
            isFocusable = true
            setOnClickListener { afterTap(action) }
        }
        row.addView(
            text(label, 16f, R.color.ink),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row.addView(text("\u203A", 22f, R.color.muted))
        page.addView(row, fullWidth())
        page.addView(
            View(this).apply { setBackgroundColor(getColor(R.color.line)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)),
        )
    }

    private fun heading(label: String) {
        page.addView(
            text(label, 13f, R.color.muted).apply {
                isAllCaps = true
                letterSpacing = 0.08f
                setPadding(0, dp(28), 0, dp(6))
            },
        )
    }

    /** Pick-one buttons, two per line. */
    private fun chips(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
        val all = mutableListOf<Pair<String, TextView>>()
        var current = selected

        fun paint() {
            for ((value, chip) in all) {
                val on = value == current
                chip.isSelected = on
                chip.setTextColor(getColor(if (on) R.color.on_ink else R.color.ink))
                chip.background = pressable(rounded(if (on) R.color.ink else R.color.chip))
            }
        }

        for (pair in options.chunked(2)) {
            val line = LinearLayout(this)
            for ((index, option) in pair.withIndex()) {
                val (value, label) = option
                val chip = text(label, 15f, R.color.ink).apply {
                    gravity = Gravity.CENTER
                    minHeight = dp(46)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        if (current != value) {
                            current = value
                            paint()
                            afterTap { onPick(value) }
                        }
                    }
                }
                all.add(value to chip)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (index > 0) params.marginStart = dp(8)
                line.addView(chip, params)
            }
            page.addView(line, fullWidth(dp(8)))
        }
        paint()
    }

    private fun showPrompt(authenticators: Int) {
        val kind = if (authenticators == Authenticators.DEVICE_CREDENTIAL) "pin" else "fingerprint"
        val builder = BiometricPrompt.Builder(this)
            .setTitle("In-app test ($kind)")
            .setAllowedAuthenticators(authenticators)
        // A cancel button is required for biometrics and forbidden with the device PIN.
        if (authenticators != Authenticators.DEVICE_CREDENTIAL) {
            builder.setNegativeButton("Cancel", mainExecutor) { _, _ ->
                EventLog.log(this, "PROMPT_CANCEL", "kind=$kind")
            }
        }
        EventLog.log(this, "PROMPT_SHOW", "kind=$kind ${EventLog.state(this)}")
        builder.build().authenticate(
            CancellationSignal(),
            mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    EventLog.log(this@MainActivity, "PROMPT_SUCCESS", "kind=$kind")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    EventLog.log(this@MainActivity, "PROMPT_ERROR", "kind=$kind code=$errorCode $errString")
                }
            },
        )
    }

    private fun saveLog() {
        val source = EventLog.file(this)
        if (!source.exists()) return
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.US))
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "-")
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "uwspike-$model-$stamp.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val message = try {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            contentResolver.openOutputStream(uri)!!.use { out -> source.inputStream().use { it.copyTo(out) } }
            "Saved to Downloads"
        } catch (e: Exception) {
            "Could not save: $e"
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
