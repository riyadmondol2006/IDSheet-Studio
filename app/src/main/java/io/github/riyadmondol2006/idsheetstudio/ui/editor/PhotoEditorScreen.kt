package io.github.riyadmondol2006.idsheetstudio.ui.editor

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Rotate90DegreesCcw
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.formatDecimal
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import io.github.riyadmondol2006.idsheetstudio.image.CropMath
import io.github.riyadmondol2006.idsheetstudio.image.FaceAlignMath
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.components.BackgroundSwatches
import io.github.riyadmondol2006.idsheetstudio.ui.components.ColorSwatches
import io.github.riyadmondol2006.idsheetstudio.ui.components.MeasurementField
import io.github.riyadmondol2006.idsheetstudio.ui.components.QuantityStepper
import io.github.riyadmondol2006.idsheetstudio.ui.components.SectionCard
import io.github.riyadmondol2006.idsheetstudio.ui.components.SegmentedChoice
import io.github.riyadmondol2006.idsheetstudio.ui.components.SwitchRow
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

@Composable
fun PhotoEditorScreen(
    projectId: String,
    photoId: String,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val vm = appViewModel(key = "editor-$photoId") { c, _ -> EditorViewModel(c, projectId, photoId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }
    var showPresets by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { vm.closed.collect { onClose() } }
    LaunchedEffect(Unit) {
        vm.messages.collect { message ->
            val needsKey = message.contains("API key", true)
            val r = snackbar.showSnackbar(message, actionLabel = if (needsKey) "Settings" else null, withDismissAction = !needsKey)
            if (needsKey && r == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenSettings()
        }
    }
    BackHandler(enabled = state.dirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit photo") },
                navigationIcon = {
                    IconButton(onClick = { if (state.dirty) confirmDiscard = true else onClose() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    Button(onClick = { vm.save(onClose) }, modifier = Modifier.padding(end = 8.dp).testTag("editor_save")) { Text("Save") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val draft = state.draft
        if (draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            CropArea(
                source = state.source,
                photo = draft,
                showGuide = showGuide,
                loading = state.loadingSource,
                onGesture = vm::gesture,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AdjustCard(draft.transform, showGuide, { showGuide = it }, state.aligningFace, vm)
                SizeCard(draft, state.unit, vm, onChoosePreset = { showPresets = true })
                SectionCard("Copies", icon = Icons.Rounded.PhotoSizeSelectLarge, subtitle = "How many prints of this photo") {
                    QuantityStepper(draft.quantity, vm::setQuantity, Modifier.testTag("editor_quantity"))
                }
                BackgroundCard(draft, vm)
                OutlinedTextField(
                    value = draft.label,
                    onValueChange = vm::setLabel,
                    label = { Text("Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPresets) {
        state.draft?.let { draft ->
            PresetSheet(
                selectedId = draft.presetId,
                unit = state.unit,
                onSelect = { vm.selectPreset(it); showPresets = false },
                onDismiss = { showPresets = false },
            )
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your edits to this photo haven't been saved.") },
            confirmButton = { TextButton(onClick = { confirmDiscard = false; onClose() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirmDiscard = false; vm.save(onClose) }) { Text("Save") } },
        )
    }
}

/** Frame (target photo) rectangle inside a canvas, keeping the photo's aspect ratio. */
private fun frameRect(canvas: Size, aspect: Float, padding: Float): Rect {
    val availW = canvas.width - 2 * padding
    val availH = canvas.height - 2 * padding
    val (w, h) = if (availW / availH > aspect) availH * aspect to availH else availW to availW / aspect
    val left = (canvas.width - w) / 2
    val top = (canvas.height - h) / 2
    return Rect(left, top, left + w, top + h)
}

@Composable
private fun CropArea(
    source: Bitmap?,
    photo: PhotoItem,
    showGuide: Boolean,
    loading: Boolean,
    onGesture: (Float, Float, Float, Float, Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspect = (photo.size.width / photo.size.height).toFloat()
    val currentAspect by rememberUpdatedState(aspect)
    val currentGesture by rememberUpdatedState(onGesture)
    var interacting by remember { mutableStateOf(false) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val guideColor = MaterialTheme.colorScheme.primaryContainer

    // clipToBounds: a zoomed photo must not paint over the controls below the crop area.
    Box(modifier.clipToBounds().background(Color(0xFF15171C)), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .testTag("crop_canvas")
                .semantics { contentDescription = "Crop area. Pinch to zoom, drag to move, twist to rotate." }
                .pointerInput(Unit) {
                    awaitPointerEventScopeSafe { interacting = it }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, rotation ->
                        val frame = frameRect(Size(size.width.toFloat(), size.height.toFloat()), currentAspect, 24.dp.toPx())
                        currentGesture(frame.width, frame.height, centroid.x - frame.left, centroid.y - frame.top, pan.x, pan.y, zoom, rotation)
                    }
                },
        ) {
            val frame = frameRect(size, aspect, 24.dp.toPx())
            drawRect(Color(photo.backgroundColor.toInt()), frame.topLeft, frame.size)
            if (source != null && !source.isRecycled) {
                val m = CropMath.matrix(source.width.toDouble(), source.height.toDouble(), frame.width.toDouble(), frame.height.toDouble(), photo.transform)
                val matrix = Matrix().apply {
                    setValues(floatArrayOf(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat(), 0f, 0f, 1f))
                    postTranslate(frame.left, frame.top)
                }
                drawIntoCanvas { it.nativeCanvas.drawBitmap(source, matrix, paint) }
            }
            // Dim everything outside the print frame.
            val scrim = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(Offset.Zero, size))
                addRect(frame)
            }
            drawPath(scrim, Color.Black.copy(alpha = 0.62f))
            drawRect(Color.White, frame.topLeft, frame.size, style = Stroke(1.5.dp.toPx()))

            if (interacting) {
                val thirdW = frame.width / 3
                val thirdH = frame.height / 3
                for (i in 1..2) {
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(frame.left + thirdW * i, frame.top), Offset(frame.left + thirdW * i, frame.bottom), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(frame.left, frame.top + thirdH * i), Offset(frame.right, frame.top + thirdH * i), 1.dp.toPx())
                }
            }
            if (showGuide) {
                // Typical passport framing: crown near the top, chin about 80% down, eyes ~42% down (same target as Auto-align face).
                val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                val headW = frame.width * 0.62f
                val headTop = frame.top + frame.height * 0.08f
                val headBottom = frame.top + frame.height * 0.82f
                drawOval(
                    guideColor,
                    topLeft = Offset(frame.center.x - headW / 2, headTop),
                    size = Size(headW, headBottom - headTop),
                    style = Stroke(2.dp.toPx(), pathEffect = dash),
                )
                val eyeY = frame.top + frame.height * FaceAlignMath.EYE_LINE.toFloat()
                drawLine(guideColor.copy(alpha = 0.8f), Offset(frame.left + frame.width * 0.12f, eyeY), Offset(frame.right - frame.width * 0.12f, eyeY), 1.5.dp.toPx(), pathEffect = dash)
            }
        }
        if (loading && source == null) CircularProgressIndicator()
    }
}

/** Tracks whether any pointer is down, without consuming events. */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.awaitPointerEventScopeSafe(onChange: (Boolean) -> Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
            onChange(event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun AdjustCard(transform: PhotoTransform, showGuide: Boolean, onGuide: (Boolean) -> Unit, aligningFace: Boolean, vm: EditorViewModel) {
    // The rotation is a quarter turn (buttons) plus a fine straighten angle (slider).
    val quarterTurns = (transform.rotation / 90.0).roundToInt()
    val straighten = transform.rotation - quarterTurns * 90.0
    SectionCard("Position & rotation", icon = Icons.Rounded.Tune, subtitle = "Pinch, drag and twist the photo, or use the controls") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { vm.rotateQuarter(false) }, modifier = Modifier.testTag("rotate_left")) { Icon(Icons.Rounded.Rotate90DegreesCcw, "Rotate left") }
            FilledTonalIconButton(onClick = { vm.rotateQuarter(true) }, modifier = Modifier.testTag("rotate_right")) { Icon(Icons.Rounded.Rotate90DegreesCw, "Rotate right") }
            FilledTonalIconButton(onClick = vm::resetTransform, modifier = Modifier.testTag("reset_transform")) { Icon(Icons.Rounded.RestartAlt, "Reset crop") }
            Text(
                "Rotation ${Math.floorMod(quarterTurns * 90, 360)}°",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).testTag("rotation_label"),
            )
            FilledTonalIconToggleButton(checked = showGuide, onCheckedChange = onGuide) { Icon(Icons.Rounded.Face, "Face guide") }
        }
        FilledTonalButton(
            onClick = { vm.autoAlignFace() },
            enabled = !aligningFace,
            modifier = Modifier.testTag("auto_align_face"),
        ) {
            if (aligningFace) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Rounded.CenterFocusStrong, null, Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(if (aligningFace) "Finding face…" else "Auto-align face")
        }
        val minLog = ln(PhotoTransform.MIN_ZOOM).toFloat()
        val maxLog = ln(PhotoTransform.MAX_ZOOM).toFloat()
        LabeledSlider("Zoom", "${formatDecimal(transform.zoom * 100, 0)}%") {
            Slider(
                value = ln(transform.zoom).toFloat().coerceIn(minLog, maxLog),
                onValueChange = { vm.setZoom(exp(it.toDouble())) },
                valueRange = minLog..maxLog,
                modifier = Modifier.testTag("zoom_slider"),
            )
        }
        LabeledSlider("Straighten", "${formatDecimal(straighten, 1)}°") {
            Slider(
                value = straighten.toFloat().coerceIn(-45f, 45f),
                onValueChange = { vm.setStraighten(it.toDouble()) },
                valueRange = -45f..45f,
                modifier = Modifier.testTag("straighten_slider"),
            )
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: String, slider: @Composable () -> Unit) {
    Column {
        Row {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        slider()
    }
}

@Composable
private fun SizeCard(photo: PhotoItem, unit: LengthUnit, vm: EditorViewModel, onChoosePreset: () -> Unit) {
    SectionCard(
        "Photo size",
        icon = Icons.Rounded.PhotoSizeSelectLarge,
        subtitle = "${photo.presetName} · ${unit.format(photo.size.width, false)} × ${unit.format(photo.size.height)}",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onChoosePreset, modifier = Modifier.testTag("choose_preset")) { Text("Choose preset") }
            OutlinedButton(onClick = vm::swapOrientation) {
                Icon(Icons.Rounded.SwapHoriz, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Swap W/H")
            }
        }
        SegmentedChoice(LengthUnit.entries, unit, vm::setUnit, { it.symbol })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MeasurementField("Width", photo.size.width, unit, { vm.setCustomSize(width = it) }, Modifier.weight(1f).testTag("photo_width"), minMm = 5.0, maxMm = 400.0)
            MeasurementField("Height", photo.size.height, unit, { vm.setCustomSize(height = it) }, Modifier.weight(1f).testTag("photo_height"), minMm = 5.0, maxMm = 400.0)
        }
        Text(
            "Official requirements change — always confirm the size with the issuing authority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackgroundCard(photo: PhotoItem, vm: EditorViewModel) {
    SectionCard("Background", icon = Icons.Rounded.FormatPaint, subtitle = "Remove the background with remove.bg, then pick a colour") {
        AnimatedContent(photo.backgroundState, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "bgstate") { bgState ->
            when {
                bgState == BackgroundState.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Removing background…")
                }
                photo.cutoutPath != null -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Background removed", modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::removeBackground) { Text("Redo") }
                    }
                    cutoutDpiHint(photo)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (bgState == BackgroundState.FAILED && photo.backgroundError != null) {
                        Text(photo.backgroundError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledTonalButton(onClick = vm::removeBackground, modifier = Modifier.testTag("editor_remove_bg")) {
                        Icon(Icons.Rounded.AutoFixHigh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (bgState == BackgroundState.FAILED) "Try again" else "Remove background")
                    }
                }
            }
        }
        val hasCutout = photo.cutoutPath != null
        // Once a cut-out exists the colour is the main choice, so it comes straight after the
        // "Background removed" row; before that it only matters around a zoomed-out photo.
        if (hasCutout) BackgroundColorPicker(photo, vm, prominent = true)
        SwitchRow(
            "Use background-removed photo",
            photo.useCutout,
            vm::setUseCutout,
            enabled = hasCutout,
            subtitle = if (!hasCutout) "Available after removing the background" else "Turn off to print the original",
        )
        if (!hasCutout) BackgroundColorPicker(photo, vm, prominent = false)
    }
}

@Composable
private fun BackgroundColorPicker(photo: PhotoItem, vm: EditorViewModel, prominent: Boolean) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Background colour", style = if (prominent) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge)
        if (!prominent) {
            Text(
                "The colour shows only where the background was removed, or around a zoomed-out photo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ColorSwatches(BackgroundSwatches, photo.backgroundColor, vm::setBackgroundColor)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showCustom = true }, modifier = Modifier.testTag("custom_color")) {
                Icon(Icons.Rounded.Palette, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Custom…")
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .background(Color(photo.backgroundColor.toInt())),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                formatHexColor(photo.backgroundColor) + if (photo.backgroundColor in BackgroundSwatches) "" else " (custom)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "Pure white (#FFFFFF) is the default and the most widely accepted.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (showCustom) {
        ColorPickerDialog(
            initial = photo.backgroundColor,
            onDismiss = { showCustom = false },
            onApply = { vm.setBackgroundColor(it); showCustom = false },
        )
    }
}

/**
 * remove.bg "preview" results are only 0.25 MP. Warn when the cut-out gives less than 300 DPI at
 * the photo's printed size (computed for the frame-filling crop at the current zoom).
 */
@Composable
private fun cutoutDpiHint(photo: PhotoItem): String? {
    val path = photo.cutoutPath ?: return null
    val size = androidx.compose.runtime.remember(path) { io.github.riyadmondol2006.idsheetstudio.image.BitmapIO.readSize(path) } ?: return null
    val (w, h) = size
    val cover = CropMath.coverScale(w.toDouble(), h.toDouble(), photo.size.width, photo.size.height, photo.transform.rotation) *
        photo.transform.zoom // mm per source px
    val dpi = (25.4 / cover).toInt()
    return if (dpi >= 300) null else
        "Cut-out is about $dpi DPI at this size (remove.bg preview quality). For sharper prints choose Full resolution in Settings (uses credits)."
}
