package io.github.riyadmondol2006.idsheetstudio.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/** Parses `#RRGGBB` or `RRGGBB` (any case, surrounding spaces ignored) into an opaque ARGB value. */
fun parseHexColor(text: String): Long? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return 0xFF000000 or hex.toLong(16)
}

/** Formats the RGB part of [argb] as `#RRGGBB`. */
fun formatHexColor(argb: Long): String = "#" + "%06X".format(Locale.ROOT, argb and 0xFFFFFF)

private fun argbOf(r: Int, g: Int, b: Int): Long = 0xFF000000 or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong()

/** Picks any opaque colour with R/G/B sliders or a hex code, with a live preview. */
@Composable
fun ColorPickerDialog(
    initial: Long,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    var red by rememberSaveable { mutableIntStateOf(((initial shr 16) and 0xFF).toInt()) }
    var green by rememberSaveable { mutableIntStateOf(((initial shr 8) and 0xFF).toInt()) }
    var blue by rememberSaveable { mutableIntStateOf((initial and 0xFF).toInt()) }
    var hex by rememberSaveable { mutableStateOf(formatHexColor(initial)) }
    val hexValid = parseHexColor(hex) != null
    val argb = argbOf(red, green, blue)

    fun setChannels(r: Int, g: Int, b: Int) {
        red = r
        green = g
        blue = b
        hex = formatHexColor(argbOf(r, g, b))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                        .background(Color(argb.toInt()))
                        .semantics { contentDescription = "Preview ${formatHexColor(argb)}" },
                )
                ChannelSlider("R", red, Color(0xFFE53935), "color_red") { setChannels(it, green, blue) }
                ChannelSlider("G", green, Color(0xFF43A047), "color_green") { setChannels(red, it, blue) }
                ChannelSlider("B", blue, Color(0xFF1E88E5), "color_blue") { setChannels(red, green, it) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { input ->
                        hex = input.take(9)
                        parseHexColor(input)?.let { c ->
                            red = ((c shr 16) and 0xFF).toInt()
                            green = ((c shr 8) and 0xFF).toInt()
                            blue = (c and 0xFF).toInt()
                        }
                    },
                    label = { Text("Hex") },
                    placeholder = { Text("#RRGGBB") },
                    isError = !hexValid,
                    supportingText = { if (!hexValid) Text("Use six hex digits, like #FFFFFF") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().testTag("color_hex_field"),
                    shape = MaterialTheme.shapes.medium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(argb) }, enabled = hexValid, modifier = Modifier.testTag("color_apply")) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, tag: String, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint),
            modifier = Modifier.weight(1f).testTag(tag).semantics { contentDescription = "$label $value" },
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End,
        )
    }
}
