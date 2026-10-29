package io.github.riyadmondol2006.idsheetstudio.ui.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.Project
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.image.PhotoRenderer
import io.github.riyadmondol2006.idsheetstudio.ui.appContainer
import io.github.riyadmondol2006.idsheetstudio.ui.components.SectionCard
import io.github.riyadmondol2006.idsheetstudio.ui.components.SegmentedChoice
import io.github.riyadmondol2006.idsheetstudio.ui.components.SwitchRow
import io.github.riyadmondol2006.idsheetstudio.ui.home.plural
import io.github.riyadmondol2006.idsheetstudio.ui.sheet.MoveRequest
import io.github.riyadmondol2006.idsheetstudio.ui.sheet.SheetCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun LayoutTab(
    project: Project,
    settings: SheetSettings,
    preview: SheetPreview?,
    onMove: (MoveRequest) -> Unit,
    onResetPositions: () -> Unit,
    onChange: ((SheetSettings) -> SheetSettings) -> Unit,
    onGoToPhotos: () -> Unit,
) {
    val renderer = appContainer().renderer
    val images = rememberSheetImages(project.photos, renderer)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 112.dp).testTag("layout_tab"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val layout = preview?.layout
        if (layout == null || layout.pageCount == 0) {
            EmptyLayout(project, layout?.isValidPrintableArea != false, onGoToPhotos)
            return@Column
        }
        val pagerState = rememberPagerState { preview.pages.size }
        Row(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Page ${pagerState.currentPage + 1} of ${layout.pageCount}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.testTag("layout_page_label"),
                )
                Text(
                    "${plural(layout.placements.size, "copy", "copies")} · ${(layout.utilization * 100).roundToInt()}% of printable area used",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth().testTag("layout_pager"),
        ) { index ->
            SheetCanvas(
                page = preview.pages[index],
                images = images,
                printableArea = layout.printableArea,
                showPrintableArea = true,
                onMove = onMove,
            )
        }
        if (layout.pageCount > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                repeat(layout.pageCount) { i ->
                    val selected = i == pagerState.currentPage
                    Box(
                        Modifier.padding(3.dp).size(if (selected) 10.dp else 7.dp).clip(CircleShape)
                            .then(Modifier),
                    ) {
                        Surface(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxSize(),
                        ) {}
                    }
                }
            }
        }

        AnimatedVisibility(layout.unplaced.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${plural(layout.unplaced.size, "copy", "copies")} ${if (layout.unplaced.size == 1) "is" else "are"} larger than the printable area and can't be placed. Reduce the margins, use a larger paper or allow rotation.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionCard("Arrangement", icon = Icons.Rounded.Dashboard, subtitle = "Photos are never stretched, overlapped or skipped") {
                SegmentedChoice(
                    LayoutStrategy.entries,
                    settings.strategy,
                    { s -> onChange { it.copy(strategy = s, manualPositions = emptyMap()) } },
                    { when (it) { LayoutStrategy.AUTO -> "Smart"; LayoutStrategy.ROWS -> "Neat rows"; LayoutStrategy.DENSE -> "Max fit" } },
                )
                Text(
                    when (settings.strategy) {
                        LayoutStrategy.AUTO -> "Uses tidy rows unless a denser arrangement saves paper."
                        LayoutStrategy.ROWS -> "Straight rows, easiest to cut with a trimmer."
                        LayoutStrategy.DENSE -> "Packs mixed sizes as tightly as possible."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    "Allow rotating photos",
                    settings.allowRotation,
                    { r -> onChange { it.copy(allowRotation = r, manualPositions = emptyMap()) } },
                    subtitle = "Turn photos 90° when it fits more on a page",
                )
            }
            SectionCard("Manual positioning", icon = Icons.Rounded.PanTool, subtitle = "Long-press a photo on the page, then drag it") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ScreenRotation, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${settings.manualPositions.size} moved by hand. Moves that overlap or leave the margins are rejected.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = onResetPositions, enabled = settings.manualPositions.isNotEmpty(), modifier = Modifier.testTag("reset_positions")) {
                    Icon(Icons.Rounded.RestartAlt, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Reset to automatic layout")
                }
            }
        }
    }
}

@Composable
private fun EmptyLayout(project: Project, validArea: Boolean, onGoToPhotos: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Icon(Icons.Rounded.Dashboard, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                !validArea -> "The margins leave no printable area. Reduce them in the Paper tab."
                project.photos.isEmpty() -> "Add photos to see them arranged on the sheet."
                else -> "Set a quantity above zero, or check that photos fit inside the margins."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onGoToPhotos) { Text("Go to photos") }
    }
}

/** Renders a preview bitmap for each photo, refreshed when a photo's revision changes. */
@Composable
fun rememberSheetImages(photos: List<PhotoItem>, renderer: PhotoRenderer): Map<String, ImageBitmap> {
    val key = photos.map { listOf(it.id, it.transform, it.activePath, it.size, it.backgroundColor) }
    val state = produceState<Map<String, ImageBitmap>>(emptyMap(), key) {
        value = withContext(Dispatchers.Default) {
            photos.mapNotNull { p -> renderer.preview(p, 700)?.let { p.id to it.asImageBitmap() } }.toMap()
        }
    }
    return state.value
}
