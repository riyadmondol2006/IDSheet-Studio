package io.github.riyadmondol2006.idsheetstudio.ui.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BorderOuter
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Margin
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.domain.model.BorderSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.LineStyle
import io.github.riyadmondol2006.idsheetstudio.domain.model.Orientation
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.ui.components.ColorSwatches
import io.github.riyadmondol2006.idsheetstudio.ui.components.Expandable
import io.github.riyadmondol2006.idsheetstudio.ui.components.MeasurementField
import io.github.riyadmondol2006.idsheetstudio.ui.components.SectionCard
import io.github.riyadmondol2006.idsheetstudio.ui.components.SegmentedChoice
import io.github.riyadmondol2006.idsheetstudio.ui.components.SwatchColors
import io.github.riyadmondol2006.idsheetstudio.ui.components.SwitchRow

@Composable
fun PaperTab(settings: SheetSettings, onChange: ((SheetSettings) -> SheetSettings) -> Unit) {
    val unit = settings.displayUnit
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp).testTag("paper_tab"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard("Units", icon = Icons.Rounded.Straighten, subtitle = "Used for every measurement in this sheet") {
            SegmentedChoice(LengthUnit.entries, unit, { u -> onChange { it.copy(displayUnit = u) } }, { it.symbol })
        }

        SectionCard("Paper", icon = Icons.Rounded.Description, subtitle = "${settings.paperName} · ${unit.format(settings.pageSize.width, false)} × ${unit.format(settings.pageSize.height)}") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaperSizes.all.forEach { paper ->
                    FilterChip(
                        selected = settings.paperId == paper.id,
                        onClick = { onChange { it.copy(paperId = paper.id, manualPositions = emptyMap()) } },
                        label = { Text(paper.name) },
                        modifier = Modifier.testTag("paper_${paper.id}"),
                    )
                }
                FilterChip(
                    selected = PaperSizes.byId(settings.paperId) == null,
                    onClick = { onChange { it.copy(paperId = PaperSizes.CUSTOM_ID, manualPositions = emptyMap()) } },
                    label = { Text("Custom") },
                    modifier = Modifier.testTag("paper_custom"),
                )
            }
            Expandable(PaperSizes.byId(settings.paperId) == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MeasurementField("Width", settings.customPaper.width, unit, { v -> onChange { it.copy(customPaper = it.customPaper.copy(width = v)) } }, Modifier.weight(1f), minMm = 20.0, maxMm = 1200.0)
                    MeasurementField("Height", settings.customPaper.height, unit, { v -> onChange { it.copy(customPaper = it.customPaper.copy(height = v)) } }, Modifier.weight(1f), minMm = 20.0, maxMm = 1200.0)
                }
            }
            SegmentedChoice(
                Orientation.entries,
                settings.orientation,
                { o -> onChange { it.copy(orientation = o, manualPositions = emptyMap()) } },
                { if (it == Orientation.PORTRAIT) "Portrait" else "Landscape" },
            )
        }

        SectionCard("Margins & spacing", icon = Icons.Rounded.Margin, subtitle = "Exact distances from the paper edge and between photos") {
            var linked by rememberSaveable { mutableStateOf(settings.margins.let { it.top == it.right && it.right == it.bottom && it.bottom == it.left }) }
            SwitchRow("Same margin on all sides", linked, { linked = it })
            val m = settings.margins
            if (linked) {
                MeasurementField("Margin", m.top, unit, { v -> onChange { it.copy(margins = Margins.all(v)) } }, Modifier.testTag("margin_all"), maxMm = 200.0)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MeasurementField("Top", m.top, unit, { v -> onChange { it.copy(margins = it.margins.copy(top = v)) } }, Modifier.weight(1f), maxMm = 200.0)
                    MeasurementField("Bottom", m.bottom, unit, { v -> onChange { it.copy(margins = it.margins.copy(bottom = v)) } }, Modifier.weight(1f), maxMm = 200.0)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MeasurementField("Left", m.left, unit, { v -> onChange { it.copy(margins = it.margins.copy(left = v)) } }, Modifier.weight(1f), maxMm = 200.0)
                    MeasurementField("Right", m.right, unit, { v -> onChange { it.copy(margins = it.margins.copy(right = v)) } }, Modifier.weight(1f), maxMm = 200.0)
                }
            }
            MeasurementField("Space between photos", settings.spacingMm, unit, { v -> onChange { it.copy(spacingMm = v) } }, Modifier.testTag("spacing_field"), maxMm = 50.0)
        }

        SectionCard("Page border", icon = Icons.Rounded.BorderOuter, trailing = {
            androidx.compose.material3.Switch(
                checked = settings.pageBorder.enabled,
                onCheckedChange = { e -> onChange { it.copy(pageBorder = it.pageBorder.copy(enabled = e)) } },
                modifier = Modifier.testTag("page_border_switch"),
            )
        }) {
            Expandable(settings.pageBorder.enabled) {
                BorderEditor(settings.pageBorder, unit, insetLabel = "Inset from paper edge") { b -> onChange { it.copy(pageBorder = b) } }
            }
        }

        SectionCard("Photo borders", icon = Icons.Rounded.CropPortrait, subtitle = "Drawn inside each photo, so sizes stay exact", trailing = {
            androidx.compose.material3.Switch(
                checked = settings.photoBorder.enabled,
                onCheckedChange = { e -> onChange { it.copy(photoBorder = it.photoBorder.copy(enabled = e)) } },
                modifier = Modifier.testTag("photo_border_switch"),
            )
        }) {
            Expandable(settings.photoBorder.enabled) {
                BorderEditor(settings.photoBorder, unit, insetLabel = "Inset from photo edge") { b -> onChange { it.copy(photoBorder = b) } }
            }
        }

        SectionCard("Cutting guides", icon = Icons.Rounded.ContentCut, subtitle = "Help trim photos precisely") {
            SegmentedChoice(
                CutGuides.entries,
                settings.cutGuides,
                { g -> onChange { it.copy(cutGuides = g) } },
                { when (it) { CutGuides.NONE -> "None"; CutGuides.CROP_MARKS -> "Crop marks"; CutGuides.OUTLINES -> "Cut lines" } },
            )
        }
    }
}

@Composable
private fun BorderEditor(border: BorderSettings, unit: LengthUnit, insetLabel: String, onChange: (BorderSettings) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MeasurementField("Thickness", border.thicknessMm, unit, { onChange(border.copy(thicknessMm = it)) }, Modifier.weight(1f), minMm = 0.05, maxMm = 10.0)
        MeasurementField(insetLabel.substringBefore(" from"), border.insetMm, unit, { onChange(border.copy(insetMm = it)) }, Modifier.weight(1f), maxMm = 50.0)
    }
    Text(insetLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    SegmentedChoice(
        LineStyle.entries,
        border.style,
        { onChange(border.copy(style = it)) },
        { when (it) { LineStyle.SOLID -> "Solid"; LineStyle.DASHED -> "Dashed"; LineStyle.DOTTED -> "Dotted" } },
    )
    ColorSwatches(SwatchColors, border.color, { onChange(border.copy(color = it)) })
}
