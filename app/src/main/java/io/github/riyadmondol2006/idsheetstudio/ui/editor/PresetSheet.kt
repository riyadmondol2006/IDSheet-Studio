package io.github.riyadmondol2006.idsheetstudio.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets

@Composable
fun PresetSheet(
    selectedId: String,
    unit: LengthUnit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text("Photo size", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.testTag("preset_list"),
        ) {
            PhotoSizePresets.byCategory().forEach { (category, presets) ->
                item(key = "h_${category.name}") {
                    Text(
                        category.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(presets, key = { it.id }) { preset ->
                    val selected = preset.id == selectedId
                    ListItem(
                        headlineContent = { Text(preset.name) },
                        supportingContent = {
                            Text(
                                "${unit.format(preset.size.width, false)} × ${unit.format(preset.size.height)}" +
                                    (preset.note?.let { " · $it" } ?: ""),
                            )
                        },
                        trailingContent = { if (selected) Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary) },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .testTag("preset_${preset.id}")
                            .clickable { onSelect(preset.id) },
                    )
                }
            }
            item(key = "custom") {
                ListItem(
                    headlineContent = { Text("Custom size") },
                    supportingContent = { Text("Type any width and height in mm, cm or inches") },
                    leadingContent = { Icon(Icons.Rounded.Edit, null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelect(PhotoSizePresets.CUSTOM_ID) },
                )
            }
        }
    }
}
