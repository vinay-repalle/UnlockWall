package io.github.vinayrepalle.unlockwall.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.vinayrepalle.unlockwall.R

/** The three things the phone needs from the user, in order. */
@Composable
fun SetupScreen(
    notifications: Boolean,
    battery: Boolean,
    autostart: Boolean,
    onAllowNotifications: () -> Unit,
    onAllowBattery: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onAutostartConfirmed: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        PageHeader(stringResource(R.string.setup_steps), onBack)
        Muted(stringResource(R.string.setup_intro), Modifier.padding(bottom = 8.dp))

        Step(1, R.string.step_notification, R.string.step_notification_note, notifications) {
            Button(onClick = onAllowNotifications) { Text(stringResource(R.string.allow)) }
        }
        Step(2, R.string.step_battery, R.string.step_battery_note, battery) {
            Button(onClick = onAllowBattery) { Text(stringResource(R.string.allow)) }
        }
        Step(3, R.string.step_autostart, R.string.step_autostart_note, autostart) {
            OutlinedButton(onClick = onOpenAppSettings) {
                Text(stringResource(R.string.open_settings))
            }
            Button(onClick = onAutostartConfirmed) {
                Text(stringResource(R.string.autostart_confirm))
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    title: Int,
    note: Int,
    done: Boolean,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.padding(vertical = 16.dp)) {
        Row {
            Text("$number. " + stringResource(title), Modifier.weight(1f), fontWeight = FontWeight.Bold)
            if (done) Text("✓ " + stringResource(R.string.done))
        }
        if (!done) {
            Muted(stringResource(note), Modifier.padding(vertical = 8.dp))
            actions()
        }
    }
    HorizontalDivider()
}
