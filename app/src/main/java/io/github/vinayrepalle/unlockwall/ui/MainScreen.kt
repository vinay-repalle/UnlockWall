package io.github.vinayrepalle.unlockwall.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.vinayrepalle.unlockwall.UnlockWallApp
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.monitor.UnlockMonitorService
import io.github.vinayrepalle.unlockwall.settings.Settings as AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the phone says about us right now. */
private data class Health(
    val running: Boolean = false,
    val battery: Boolean = false,
    val notifications: Boolean = false,
    val log: List<String> = emptyList(),
)

private fun readHealth(app: UnlockWallApp) = Health(
    running = UnlockMonitorService.running,
    battery = app.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(app.packageName),
    notifications = app.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
    log = app.diagnostics.recent(40),
)

/** Plain first version of the screen; the proper design is Phase 4. */
@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as UnlockWallApp
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    var health by remember { mutableStateOf(Health()) }
    var pictures by remember { mutableStateOf(app.cache.available()) }
    var pictureVersion by remember { mutableIntStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var showLog by rememberSaveable { mutableStateOf(false) }

    // Refreshes only while the screen is visible.
    val lifecycle = (context as ComponentActivity).lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                health = withContext(Dispatchers.IO) { readHealth(app) }
                delay(1_000)
            }
        }
    }
    // Switched on but not running (stopped by the phone): start again.
    LaunchedEffect(settings?.enabled) {
        if (settings?.enabled == true && !UnlockMonitorService.running) UnlockMonitorService.start(app)
    }

    val askNotifications =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    fun picturesChanged(current: AppSettings?) {
        pictures = app.cache.available()
        pictureVersion++
        if (current?.enabled == true) UnlockMonitorService.picturesChanged(app)
    }

    fun setSwitch(on: Boolean) {
        scope.launch {
            app.settings.setEnabled(on)
            if (on) {
                askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                UnlockMonitorService.start(app)
            } else {
                UnlockMonitorService.switchOff(app)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("UnlockWall", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        val enabled = settings?.enabled == true
        val ready = Slot.NORMAL in pictures
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (enabled) "On" else "Off", fontSize = 20.sp)
                Muted(
                    when {
                        !ready -> "Choose a Normal picture first."
                        enabled && !health.running -> "Not running."
                        enabled -> "Changes start about 1 minute after switching on."
                        else -> "Your wallpaper is not changed."
                    },
                )
            }
            Switch(checked = enabled, enabled = ready || enabled, onCheckedChange = ::setSwitch)
        }

        Heading("Pictures")
        for (slot in Slot.entries) {
            PictureRow(
                slot = slot,
                version = pictureVersion,
                app = app,
                onPicked = { uri ->
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            app.cache.save(slot, ImageDecoder.createSource(app.contentResolver, uri))
                        }
                        message = if (saved) "" else "That picture could not be used."
                        if (saved) picturesChanged(settings)
                    }
                },
                onRemove = {
                    app.cache.remove(slot)
                    picturesChanged(settings)
                },
            )
            HorizontalDivider()
        }
        if (message.isNotEmpty()) {
            Text(message, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(8.dp))
        Muted(
            "This is not a security lock. Do not use a picture that must never be seen. " +
                "The app guesses from how fast you unlock. Do not use it with Extend Unlock " +
                "(Smart Lock): a phone kept unlocked could show your Quick picture.",
        )

        Heading("Phone setup")
        HealthRow("Service running", health.running)
        HealthRow("Unrestricted battery", health.battery, "Allow") {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", context.packageName, null),
                ),
            )
        }
        HealthRow("Notification allowed", health.notifications, "Allow") {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Muted("Also turn on Autostart for UnlockWall in your phone's settings.")
        TextButton(onClick = { openAppSettings(context) }) { Text("Open app settings") }

        Heading("Log")
        Muted(health.log.lastOrNull() ?: "Nothing yet.")
        TextButton(onClick = { showLog = !showLog }) { Text(if (showLog) "Hide log" else "Show log") }
        if (showLog) {
            Text(health.log.asReversed().joinToString("\n"), fontSize = 12.sp)
        }
    }
}

private fun openAppSettings(context: Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

@Composable
private fun Heading(text: String) {
    Spacer(Modifier.height(28.dp))
    Text(text.uppercase(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Muted(text: String) {
    Text(text, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HealthRow(
    name: String,
    good: Boolean,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, Modifier.weight(1f))
        if (!good && action != null) TextButton(onClick = onAction) { Text(action) }
        Text(if (good) "Yes" else "No")
    }
}

@Composable
private fun PictureRow(
    slot: Slot,
    version: Int,
    app: UnlockWallApp,
    onPicked: (Uri) -> Unit,
    onRemove: () -> Unit,
) {
    val (title, note) = when (slot) {
        Slot.NORMAL -> "Normal" to "While locked, and whenever unsure. Needed."
        Slot.QUICK -> "Quick unlock" to "Usually finger or face."
        Slot.SLOW -> "Slow unlock" to "Usually PIN. Anyone with your PIN sees it."
    }
    val thumbnail = remember(version) {
        app.cache.file(slot)?.let {
            BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 8 })
        }?.asImageBitmap()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) onPicked(it)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val shape = RoundedCornerShape(8.dp)
        val box = Modifier.size(width = 48.dp, height = 96.dp).clip(shape)
        if (thumbnail != null) {
            Image(thumbnail, contentDescription = "$title picture", box, contentScale = ContentScale.Crop)
        } else {
            Box(box.background(MaterialTheme.colorScheme.surfaceVariant))
        }
        Column(Modifier.weight(1f)) {
            Text(title)
            Muted(note)
            Row {
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                ) { Text(if (thumbnail == null) "Choose" else "Change") }
                if (thumbnail != null && slot != Slot.NORMAL) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }
        }
    }
}
