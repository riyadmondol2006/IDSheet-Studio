package io.github.riyadmondol2006.idsheetstudio.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.formatDecimal
import io.github.riyadmondol2006.idsheetstudio.core.parseDecimal
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.image.PhotoRenderer
import io.github.riyadmondol2006.idsheetstudio.ui.theme.ExpressiveMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A rounded, tonal section with a title used across settings-style screens. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                icon = {
                    if (icon != null && option != selected) Icon(icon(option), null, Modifier.size(18.dp))
                    else SegmentedButtonDefaults.Icon(option == selected)
                },
                label = { Text(label(option), maxLines = 1) },
            )
        }
    }
}

/**
 * A text field editing a physical length. The value is always stored in millimetres; the user
 * types in [unit]. Invalid or out-of-range input is flagged without changing the stored value.
 */
@Composable
fun MeasurementField(
    label: String,
    valueMm: Double,
    unit: LengthUnit,
    onValueMm: (Double) -> Unit,
    modifier: Modifier = Modifier,
    minMm: Double = 0.0,
    maxMm: Double = 2000.0,
    enabled: Boolean = true,
) {
    fun display(mm: Double) = formatDecimal(unit.fromMm(mm), unit.decimals)
    var text by remember(unit) { mutableStateOf(display(valueMm)) }
    var error by remember { mutableStateOf<String?>(null) }
    // Keep the text in sync when the value changes elsewhere (e.g. preset or unit change).
    LaunchedEffect(valueMm, unit) {
        val parsed = parseDecimal(text)?.let(unit::toMm)
        if (parsed == null || kotlin.math.abs(parsed - valueMm) > 1e-3) {
            text = display(valueMm)
            error = null
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val mm = parseDecimal(input)?.let(unit::toMm)
            error = when {
                mm == null -> "Enter a number"
                mm < minMm - 1e-9 -> "Min ${unit.format(minMm)}"
                mm > maxMm + 1e-9 -> "Max ${unit.format(maxMm)}"
                else -> null
            }
            if (error == null && mm != null) onValueMm(mm)
        },
        modifier = modifier,
        enabled = enabled,
        label = { Text(label, maxLines = 1) },
        suffix = { Text(unit.symbol) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = MaterialTheme.shapes.medium,
    )
}

/** − value + stepper with springy feedback. */
@Composable
fun QuantityStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    range: IntRange = 0..PhotoItem.MAX_QUANTITY,
    label: String = "copies",
) {
    var bumped by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (bumped) 1.18f else 1f, ExpressiveMotion.spatialFast(), label = "bump") { bumped = false }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onValueChange((value - 1).coerceIn(range)); bumped = true },
            enabled = value > range.first,
        ) { Icon(Icons.Rounded.Remove, "Fewer $label") }
        Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.scale(scale).semantics { contentDescription = "$value $label" },
                textAlign = TextAlign.Center,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(
            onClick = { onValueChange((value + 1).coerceIn(range)); bumped = true },
            enabled = value < range.last,
        ) { Icon(Icons.Rounded.Add, "More $label") }
    }
}

val SwatchColors: List<Long> = listOf(
    0xFF000000, 0xFF424242, 0xFF9E9E9E, 0xFFFFFFFF,
    0xFF1E5BD8, 0xFF00897B, 0xFFC62828, 0xFFF9A825,
)

val BackgroundSwatches: List<Long> = listOf(
    0xFFFFFFFF, 0xFFF2F2F2, 0xFFDDE7F5, 0xFFB8D4F0, 0xFF4A8FE0, 0xFFE53935,
)

@Composable
fun ColorSwatches(
    colors: List<Long>,
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            val ring by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                label = "ring",
            )
            val color = Color(argb.toInt())
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .border(BorderStroke(if (isSelected) 3.dp else 1.dp, ring), CircleShape)
                    .padding(if (isSelected) 5.dp else 2.dp)
                    .clip(CircleShape)
                    .background(color)
                    .clickable { onSelect(argb) }
                    .semantics { contentDescription = "Colour #" + "%06X".format(argb and 0xFFFFFF) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    val onColor = if (color.luminanceApprox() > 0.5f) Color.Black else Color.White
                    Icon(Icons.Rounded.Check, null, tint = onColor, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

/** Asynchronously renders a cropped preview of [photo]. */
@Composable
fun rememberPhotoPreview(photo: PhotoItem?, renderer: PhotoRenderer, longestPx: Int = 480): ImageBitmap? {
    val state = produceState<ImageBitmap?>(null, photo?.id, photo?.transform, photo?.activePath, photo?.size, photo?.backgroundColor) {
        value = photo?.let {
            withContext(Dispatchers.Default) { renderer.preview(it, longestPx)?.asImageBitmap() }
        }
    }
    return state.value
}

@Composable
fun PhotoThumbnail(
    photo: PhotoItem?,
    renderer: PhotoRenderer,
    modifier: Modifier = Modifier,
    longestPx: Int = 480,
) {
    val bitmap = rememberPhotoPreview(photo, renderer, longestPx)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
        AnimatedVisibility(bitmap != null, enter = fadeIn(ExpressiveMotion.effectsSlow()), exit = fadeOut()) {
            if (bitmap != null) {
                Image(bitmap, contentDescription = photo?.label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
        if (bitmap == null) {
            Icon(Icons.Rounded.Image, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}

/** Shows [content] with an expressive expand/collapse animation. */
@Composable
fun Expandable(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible,
        enter = expandVertically(ExpressiveMotion.size) + fadeIn(ExpressiveMotion.effects()),
        exit = shrinkVertically(ExpressiveMotion.size) + fadeOut(ExpressiveMotion.effects()),
    ) { Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() } }
}

fun Bitmap.asImageBitmapSafe(): ImageBitmap? = if (isRecycled) null else asImageBitmap()
