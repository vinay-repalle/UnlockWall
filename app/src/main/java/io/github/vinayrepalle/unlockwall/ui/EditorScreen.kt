package io.github.vinayrepalle.unlockwall.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinayrepalle.unlockwall.R
import io.github.vinayrepalle.unlockwall.core.Slot
import io.github.vinayrepalle.unlockwall.wallpaper.Adjustment
import io.github.vinayrepalle.unlockwall.wallpaper.CropMath
import io.github.vinayrepalle.unlockwall.wallpaper.Pictures
import io.github.vinayrepalle.unlockwall.wallpaper.WallpaperCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface Loaded {
    data object Waiting : Loaded
    data object Failed : Loaded
    data class Ready(val picture: Bitmap) : Loaded
}

/**
 * Shows a slot's picture in a frame shaped like the screen, to zoom, move and rotate.
 * Nothing changes until Save.
 *
 * @param uri a newly chosen photo, or null to adjust the saved picture.
 */
@Composable
fun EditorScreen(
    slot: Slot,
    uri: Uri?,
    cache: WallpaperCache,
    onSaved: () -> Unit,
    onChangePhoto: () -> Unit,
    onRemove: () -> Unit,
    onLeave: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Another slot or another photo starts afresh.
    val source = "$slot|$uri"
    val saved = remember(source) { if (uri == null) cache.editable(slot) else null }
    val initial = saved?.second ?: Adjustment()
    val hasSaved = remember(source) { cache.file(slot) != null }

    var loaded by remember(source) { mutableStateOf<Loaded>(Loaded.Waiting) }
    var saving by remember { mutableStateOf(false) }
    var failedToSave by remember(source) { mutableStateOf(false) }
    var asking by rememberSaveable(source) { mutableStateOf(false) }

    // Saved so that the position survives the screen being rebuilt.
    var rotation by rememberSaveable(source) { mutableIntStateOf(initial.rotation) }
    var zoom by rememberSaveable(source) { mutableFloatStateOf(initial.zoom) }
    var panX by rememberSaveable(source) { mutableFloatStateOf(initial.panX) }
    var panY by rememberSaveable(source) { mutableFloatStateOf(initial.panY) }

    fun adjustment() = Adjustment(rotation, zoom, panX, panY)
    fun apply(value: Adjustment) {
        rotation = value.rotation
        zoom = value.zoom
        panX = value.panX
        panY = value.panY
    }

    LaunchedEffect(source) {
        val picture = withContext(Dispatchers.IO) {
            val from = when {
                uri != null -> ImageDecoder.createSource(context.contentResolver, uri)
                else -> saved?.first?.let(ImageDecoder::createSource)
            }
            from?.let(Pictures::decode)
        }
        loaded = if (picture == null) Loaded.Failed else Loaded.Ready(picture)
    }

    val picture = (loaded as? Loaded.Ready)?.picture
    // A new photo is itself an unsaved change.
    val changed = picture != null && (uri != null || adjustment() != initial)

    fun save() {
        val chosen = picture ?: return
        val current = adjustment()
        saving = true
        scope.launch {
            val done = withContext(Dispatchers.IO) {
                cache.save(slot, chosen, current, newPhoto = uri != null)
            }
            saving = false
            failedToSave = !done
            if (done) onSaved()
        }
    }

    fun leave() {
        if (saving) return
        if (changed) asking = true else onLeave()
    }

    BackHandler { leave() }

    if (asking) {
        AlertDialog(
            // Tapping outside goes on editing.
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = {
                Text(
                    stringResource(
                        if (hasSaved) R.string.unsaved_text else R.string.unsaved_text_new,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        save()
                    },
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onLeave()
                    },
                ) { Text(stringResource(R.string.discard)) }
            },
        )
    }

    val description = stringResource(R.string.preview_description)

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp)) {
        TextButton(onClick = ::leave) { Text("‹ " + stringResource(R.string.back)) }
        Text(
            stringResource(R.string.adjust_title, slotName(slot)),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Muted(stringResource(R.string.adjust_hint))

        Box(
            Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val state = loaded) {
                Loaded.Waiting -> Muted(stringResource(R.string.loading))
                Loaded.Failed -> Text(
                    stringResource(R.string.picture_unreadable),
                    color = MaterialTheme.colorScheme.error,
                )

                is Loaded.Ready -> Canvas(
                    Modifier
                        .aspectRatio(
                            cache.width.toFloat() / cache.height,
                            matchHeightConstraintsFirst = true,
                        )
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        // Keyed on the source too, so the gesture writes to its state.
                        .pointerInput(source, state.picture) {
                            detectTransformGestures { centroid, drag, zoomChange, _ ->
                                apply(
                                    CropMath.gesture(
                                        adjustment(), zoomChange, drag.x, drag.y,
                                        centroid.x, centroid.y,
                                        state.picture.width, state.picture.height,
                                        size.width.toFloat(), size.height.toFloat(),
                                    ),
                                )
                            }
                        }
                        .semantics { contentDescription = description },
                ) {
                    val current = adjustment()
                    drawIntoCanvas {
                        Pictures.draw(it.nativeCanvas, state.picture, current, size.width, size.height)
                    }
                }
            }
        }

        if (failedToSave) {
            Text(stringResource(R.string.picture_not_saved), color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { apply(CropMath.rotate(adjustment())) },
                enabled = picture != null,
            ) { Text(stringResource(R.string.rotate)) }
            TextButton(
                onClick = { apply(Adjustment()) },
                enabled = picture != null,
            ) { Text(stringResource(R.string.reset)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onChangePhoto) { Text(stringResource(R.string.change_picture)) }
            // Normal cannot be removed: without it nothing could be restored.
            if (hasSaved && slot != Slot.NORMAL) {
                TextButton(onClick = onRemove) { Text(stringResource(R.string.remove_picture)) }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Button(enabled = changed && !saving, onClick = ::save) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}
