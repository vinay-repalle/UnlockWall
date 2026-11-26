package io.github.vinayrepalle.unlockwall.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.vinayrepalle.unlockwall.R
import io.github.vinayrepalle.unlockwall.diagnostics.LogExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LogScreen(lines: List<String>, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val entries = ActivityText.entries(lines)
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        PageHeader(stringResource(R.string.activity_log), onBack)
        OutlinedButton(
            onClick = {
                scope.launch {
                    val name = withContext(Dispatchers.IO) { LogExport.save(context, lines) }
                    val message = if (name == null) {
                        context.getString(R.string.log_not_saved)
                    } else {
                        context.getString(R.string.log_saved, name)
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
            enabled = lines.isNotEmpty(),
        ) { Text(stringResource(R.string.save_log)) }
        Muted(stringResource(R.string.save_log_note), Modifier.padding(bottom = 8.dp))
        if (entries.isEmpty()) Muted(stringResource(R.string.log_empty))
        LazyColumn {
            items(entries) { entry ->
                // Read as one item; stacked so that large text has room.
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp)
                        .semantics(mergeDescendants = true) {},
                ) {
                    Text(stringResource(label(entry.kind)))
                    Muted(entry.time)
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
