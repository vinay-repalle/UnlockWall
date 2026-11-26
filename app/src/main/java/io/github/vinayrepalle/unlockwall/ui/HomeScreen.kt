package io.github.vinayrepalle.unlockwall.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinayrepalle.unlockwall.R
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.wallpaper.WallpaperCache

@Composable
fun HomeScreen(
    cache: WallpaperCache,
    pictureVersion: Int,
    enabled: Boolean,
    running: Boolean,
    stepsLeft: Int,
    onSwitch: (Boolean) -> Unit,
    onAdd: (Slot) -> Unit,
    onOpen: (Slot) -> Unit,
    onSetup: () -> Unit,
    onLog: () -> Unit,
) {
    var explain by rememberSaveable { mutableStateOf(false) }
    val ready = remember(pictureVersion) { Slot.NORMAL in cache.available() }
    val card = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            Modifier.semantics { heading() },
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .clip(card)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    stringResource(if (enabled) R.string.switch_on else R.string.switch_off),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Muted(
                    stringResource(
                        when {
                            enabled && !running -> R.string.status_not_running
                            enabled -> R.string.status_working
                            !ready -> R.string.status_need_normal
                            else -> R.string.status_ready
                        },
                    ),
                )
            }
            val switchName = stringResource(R.string.switch_description)
            Switch(
                checked = enabled,
                onCheckedChange = onSwitch,
                modifier = Modifier.semantics { contentDescription = switchName },
                enabled = ready || enabled,
            )
        }

        if (stepsLeft > 0) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(card)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = onSetup)
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.setup_card), fontWeight = FontWeight.Bold)
                    Muted(pluralStringResource(R.plurals.setup_left, stepsLeft, stepsLeft))
                }
                Arrow()
            }
        }

        Spacer(Modifier.height(28.dp))
        Text(
            stringResource(R.string.your_pictures),
            Modifier.semantics { heading() },
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Muted(stringResource(R.string.pictures_hint))
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (slot in Slot.entries) {
                PictureTile(
                    modifier = Modifier.weight(1f),
                    slot = slot,
                    cache = cache,
                    version = pictureVersion,
                    onAdd = { onAdd(slot) },
                    onOpen = { onOpen(slot) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Muted(stringResource(R.string.slot_normal_note))
        Muted(stringResource(R.string.slot_quick_note))
        Muted(stringResource(R.string.slot_slow_note))

        Spacer(Modifier.height(24.dp))
        LinkRow(stringResource(R.string.how_it_works)) { explain = !explain }
        if (explain) {
            Muted(stringResource(R.string.how_it_works_text), Modifier.padding(vertical = 12.dp))
        }
        LinkRow(stringResource(R.string.setup_steps), onSetup)
        LinkRow(stringResource(R.string.activity_log), onLog)
    }
}

@Composable
private fun PictureTile(
    modifier: Modifier,
    slot: Slot,
    cache: WallpaperCache,
    version: Int,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
) {
    val name = slotName(slot)
    val thumbnail = remember(version) {
        cache.file(slot)?.let {
            BitmapFactory.decodeFile(it.path, BitmapFactory.Options().apply { inSampleSize = 4 })
        }?.asImageBitmap()
    }
    val description = stringResource(
        if (thumbnail == null) R.string.empty_picture_description else R.string.picture_description,
        name,
    )

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(cache.width.toFloat() / cache.height)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { if (thumbnail == null) onAdd() else onOpen() }
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Muted("+ " + stringResource(R.string.add_picture))
            }
        }
        Text(name, Modifier.padding(top = 6.dp))
    }
}
