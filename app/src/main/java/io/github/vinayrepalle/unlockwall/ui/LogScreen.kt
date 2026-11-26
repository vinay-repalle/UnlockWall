package io.github.vinayrepalle.unlockwall.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.vinayrepalle.unlockwall.R

@Composable
fun LogScreen(lines: List<String>, onBack: () -> Unit) {
    val entries = ActivityText.entries(lines)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageHeader(stringResource(R.string.activity_log), onBack)
        if (entries.isEmpty()) Muted(stringResource(R.string.log_empty))
        LazyColumn {
            items(entries) { entry ->
                Row(Modifier.padding(vertical = 10.dp)) {
                    Muted(entry.time, Modifier.width(96.dp))
                    Text(stringResource(label(entry.kind)))
                }
                HorizontalDivider()
            }
        }
    }
}

private fun label(kind: ActivityKind) = when (kind) {
    ActivityKind.QUICK -> R.string.log_quick
    ActivityKind.FACE -> R.string.log_face
    ActivityKind.SLOW -> R.string.log_slow
    ActivityKind.UNSURE -> R.string.log_unsure
    ActivityKind.JUST_STARTED -> R.string.log_just_started
    ActivityKind.SHUTDOWN -> R.string.log_shutdown
    ActivityKind.STARTED -> R.string.log_started
    ActivityKind.SWITCHED_OFF -> R.string.log_switched_off
    ActivityKind.FAILED -> R.string.log_failed
}
