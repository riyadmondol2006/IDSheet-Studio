package io.github.riyadmondol2006.idsheetstudio.ui.project

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoCameraFront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.Project
import io.github.riyadmondol2006.idsheetstudio.ui.appContainer
import io.github.riyadmondol2006.idsheetstudio.ui.components.PhotoThumbnail
import io.github.riyadmondol2006.idsheetstudio.ui.components.QuantityStepper
import io.github.riyadmondol2006.idsheetstudio.ui.home.plural
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion

@Composable
fun PhotosTab(
    project: Project,
    preview: SheetPreview?,
    onAdd: () -> Unit,
    onEdit: (PhotoItem) -> Unit,
    onQuantity: (PhotoItem, Int) -> Unit,
    onDuplicate: (PhotoItem) -> Unit,
    onDelete: (PhotoItem) -> Unit,
    onRemoveBackground: (PhotoItem) -> Unit,
    onRemoveAllBackgrounds: () -> Unit,
) {
    if (project.photos.isEmpty()) {
        EmptyPhotos(onAdd)
        return
    }
    val unit = project.settings.displayUnit
    var confirmDelete by remember { mutableStateOf<PhotoItem?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("photos_list"),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            SummaryCard(project, preview, onRemoveAllBackgrounds)
        }
        items(project.photos, key = { it.id }) { photo ->
            PhotoCard(
                photo = photo,
                unitLabel = "${unit.format(photo.size.width, false)} × ${unit.format(photo.size.height)}",
                onEdit = { onEdit(photo) },
                onQuantity = { onQuantity(photo, it) },
                onDuplicate = { onDuplicate(photo) },
                onDelete = { confirmDelete = photo },
                onRemoveBackground = { onRemoveBackground(photo) },
                modifier = Modifier.animateItem(fadeInSpec = ExpressiveMotion.effects(), placementSpec = ExpressiveMotion.offset, fadeOutSpec = ExpressiveMotion.effects()),
            )
        }
    }

    confirmDelete?.let { photo ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text("Remove photo?") },
            text = { Text("\"${photo.label}\" and all of its copies will be removed from this sheet.") },
            confirmButton = { TextButton(onClick = { onDelete(photo); confirmDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SummaryCard(project: Project, preview: SheetPreview?, onRemoveAllBackgrounds: () -> Unit) {
    val pages = preview?.layout?.pageCount ?: 0
    val unplaced = preview?.layout?.unplaced?.size ?: 0
    val pending = project.photos.count { it.cutoutPath == null && it.backgroundState != BackgroundState.PROCESSING }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "${plural(project.photos.size, "photo")} · ${plural(project.totalCopies, "copy", "copies")}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                buildString {
                    append(project.settings.paperName)
                    append(" · ")
                    append(if (pages == 1) "1 page" else "$pages pages")
                    if (unplaced > 0) append(" · $unplaced don't fit")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("summary_pages"),
            )
            if (pending > 0) {
                FilledTonalButton(onClick = onRemoveAllBackgrounds, modifier = Modifier.testTag("remove_all_bg")) {
                    Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (pending == 1) "Remove background" else "Remove all backgrounds ($pending)")
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: PhotoItem,
    unitLabel: String,
    onEdit: () -> Unit,
    onQuantity: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onRemoveBackground: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderer = appContainer().renderer
    var menu by remember { mutableStateOf(false) }
    ElevatedCard(onClick = onEdit, modifier = modifier.fillMaxWidth().testTag("photo_card"), shape = MaterialTheme.shapes.large) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            val aspect = (photo.size.width / photo.size.height).toFloat()
            PhotoThumbnail(
                photo,
                renderer,
                Modifier.width(76.dp).aspectRatio(aspect.coerceIn(0.4f, 2.5f)).clip(MaterialTheme.shapes.small),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(photo.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { menu = true }, modifier = Modifier.testTag("photo_menu")) { Icon(Icons.Rounded.MoreVert, "More options") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                            DropdownMenuItem(text = { Text("Edit & crop") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                            DropdownMenuItem(text = { Text("Remove background") }, leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null) }, onClick = { menu = false; onRemoveBackground() })
                            DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { menu = false; onDuplicate() })
                            DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
                Text("${photo.presetName} · $unitLabel", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BackgroundChip(photo, onRemoveBackground)
                QuantityStepper(photo.quantity, onQuantity, Modifier.testTag("quantity_stepper"))
            }
        }
    }
}

@Composable
private fun BackgroundChip(photo: PhotoItem, onRemoveBackground: () -> Unit) {
    AnimatedContent(photo.backgroundState to photo.useCutout, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bg") { (state, cutout) ->
        when {
            state == BackgroundState.PROCESSING -> AssistChip(
                onClick = {},
                label = { Text("Removing background…") },
                leadingIcon = { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) },
            )
            cutout -> AssistChip(
                onClick = {},
                label = { Text("Background removed") },
                leadingIcon = { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.primary),
            )
            state == BackgroundState.FAILED -> AssistChip(
                onClick = onRemoveBackground,
                label = { Text("Retry background removal", maxLines = 1) },
                leadingIcon = { Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(leadingIconContentColor = MaterialTheme.colorScheme.error),
            )
            else -> AssistChip(
                onClick = onRemoveBackground,
                label = { Text("Remove background") },
                leadingIcon = { Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun EmptyPhotos(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(120.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PhotoCameraFront, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("Add your photos", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Pick one or more portraits. You can crop each one, remove its background and choose how many copies to print.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onAdd, modifier = Modifier.testTag("empty_add_photos")) {
            Icon(Icons.Rounded.AddPhotoAlternate, null)
            Spacer(Modifier.width(8.dp))
            Text("Choose photos")
        }
    }
}
