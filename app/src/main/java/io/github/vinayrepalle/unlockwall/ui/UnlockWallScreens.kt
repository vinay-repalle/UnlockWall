package io.github.vinayrepalle.unlockwall.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.vinayrepalle.unlockwall.UnlockWallApp
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.monitor.UnlockMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Page { HOME, EDIT, SETUP, LOG }

/** What the phone says about us right now. */
private data class Health(
    val running: Boolean = false,
    // Assumed fine until read, so the setup card does not flash on opening.
    val battery: Boolean = true,
    val notifications: Boolean = true,
    val log: List<String> = emptyList(),
)

private fun readHealth(app: UnlockWallApp) = Health(
    running = UnlockMonitorService.running,
    battery = app.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(app.packageName),
    notifications = app.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
    log = app.diagnostics.recent(1_000),
)

/** Holds the shared state and decides which page is shown. */
@Composable
fun UnlockWallScreens(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val app = context.applicationContext as UnlockWallApp
    val scope = rememberCoroutineScope()
    val settings by app.settings.settings.collectAsState(initial = null)
    var health by remember { mutableStateOf(Health()) }
    var pictureVersion by remember { mutableIntStateOf(0) }

    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var editSlot by rememberSaveable { mutableStateOf(Slot.NORMAL) }
    var editUri by rememberSaveable { mutableStateOf("") }

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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) {
            editUri = it.toString()
            page = Page.EDIT
        }
    }

    fun picturesChanged() {
        pictureVersion++
        if (settings?.enabled == true) UnlockMonitorService.picturesChanged(app)
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

    fun appUri() = Uri.fromParts("package", context.packageName, null)

    fun choosePhoto() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    // The adjust page handles Back itself, to ask about unsaved changes.
    BackHandler(enabled = page == Page.SETUP || page == Page.LOG) { page = Page.HOME }

    val autostart = settings?.autostartConfirmed ?: true
    val stepsLeft = listOf(health.notifications, health.battery, autostart).count { !it }

    Box(modifier) {
        when (page) {
            Page.HOME -> HomeScreen(
                cache = app.cache,
                pictureVersion = pictureVersion,
                enabled = settings?.enabled == true,
                running = health.running,
                stepsLeft = stepsLeft,
                onSwitch = ::setSwitch,
                onAdd = { slot ->
                    editSlot = slot
                    choosePhoto()
                },
                onOpen = { slot ->
                    editSlot = slot
                    // No new photo: adjust what is saved.
                    editUri = ""
                    page = Page.EDIT
                },
                onSetup = { page = Page.SETUP },
                onLog = { page = Page.LOG },
            )

            Page.EDIT -> EditorScreen(
                slot = editSlot,
                uri = editUri.takeIf { it.isNotEmpty() }?.let(Uri::parse),
                cache = app.cache,
                onSaved = {
                    picturesChanged()
                    page = Page.HOME
                },
                onChangePhoto = { choosePhoto() },
                onRemove = {
                    app.cache.remove(editSlot)
                    picturesChanged()
                    page = Page.HOME
                },
                onLeave = { page = Page.HOME },
            )

            Page.SETUP -> SetupScreen(
                notifications = health.notifications,
                battery = health.battery,
                autostart = autostart,
                onAllowNotifications = {
                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                onAllowBattery = {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, appUri()),
                    )
                },
                onOpenAppSettings = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, appUri()),
                    )
                },
                onAutostartConfirmed = { scope.launch { app.settings.confirmAutostart() } },
                onBack = { page = Page.HOME },
            )

            Page.LOG -> LogScreen(health.log) { page = Page.HOME }
        }
    }
}
