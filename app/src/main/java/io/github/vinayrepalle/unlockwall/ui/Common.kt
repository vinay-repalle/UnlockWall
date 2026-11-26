package io.github.vinayrepalle.unlockwall.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinayrepalle.unlockwall.R
import io.github.vinayrepalle.unlockwall.core.Slot

@Composable
fun slotName(slot: Slot): String = stringResource(
    when (slot) {
        Slot.NORMAL -> R.string.slot_normal
        Slot.QUICK -> R.string.slot_quick
        Slot.SLOW -> R.string.slot_slow
    },
)

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Decoration only: screen readers skip it. */
@Composable
fun Arrow() {
    Text(
        "›",
        Modifier.clearAndSetSemantics {},
        fontSize = 22.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A tappable line with an arrow and a divider under it. */
@Composable
fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f))
        Arrow()
    }
    HorizontalDivider()
}

/** Title of a second page, with the way back. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit) {
    TextButton(onClick = onBack) { Text("‹ " + stringResource(R.string.back)) }
    Text(
        title,
        Modifier.padding(top = 4.dp, bottom = 16.dp).semantics { heading() },
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
    )
}
