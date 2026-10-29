package io.github.riyadmondol2006.idsheetstudio.core

import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToLong

/** Millimetres per inch, by definition. */
const val MM_PER_INCH = 25.4

/** PDF user-space units (points) per inch. */
const val POINTS_PER_INCH = 72.0

/** Physical length units supported by the editor. All internal values are stored in millimetres. */
@Serializable
enum class LengthUnit(val symbol: String, val mmPerUnit: Double, val decimals: Int) {
    MM("mm", 1.0, 1),
    CM("cm", 10.0, 2),
    INCH("in", MM_PER_INCH, 3);

    fun fromMm(mm: Double): Double = mm / mmPerUnit
    fun toMm(value: Double): Double = value * mmPerUnit

    /** Formats a millimetre value in this unit, trimming trailing zeros. */
    fun format(mm: Double, withSymbol: Boolean = true): String {
        val text = formatDecimal(fromMm(mm), decimals)
        return if (withSymbol) "$text $symbol" else text
    }
}

fun mmToPoints(mm: Double): Double = mm / MM_PER_INCH * POINTS_PER_INCH
fun pointsToMm(points: Double): Double = points / POINTS_PER_INCH * MM_PER_INCH

/** Converts a physical length to a pixel count at the given resolution. */
fun mmToPixels(mm: Double, dpi: Int): Int = (mm / MM_PER_INCH * dpi).roundToLong().toInt().coerceAtLeast(1)

/** Locale-independent decimal formatting with trailing zeros removed (always uses '.'). */
fun formatDecimal(value: Double, maxDecimals: Int): String {
    var factor = 1L
    repeat(maxDecimals) { factor *= 10 }
    val scaled = (abs(value) * factor).roundToLong()
    val intPart = scaled / factor
    var frac = (scaled % factor).toString().padStart(maxDecimals, '0').trimEnd('0')
    if (maxDecimals == 0) frac = ""
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    return if (frac.isEmpty()) "$sign$intPart" else "$sign$intPart.$frac"
}

/** Parses user input such as "35", "3.5" or "3,5". Returns null for invalid input. */
fun parseDecimal(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }
