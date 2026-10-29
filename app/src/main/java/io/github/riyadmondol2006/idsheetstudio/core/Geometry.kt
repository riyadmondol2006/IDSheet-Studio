package io.github.riyadmondol2006.idsheetstudio.core

import kotlinx.serialization.Serializable

/** Width/height in millimetres. */
@Serializable
data class SizeMm(val width: Double, val height: Double) {
    val isLandscape: Boolean get() = width > height
    fun rotated(): SizeMm = SizeMm(height, width)
    fun area(): Double = width * height
}

/** Axis-aligned rectangle in millimetres with a top-left origin. */
@Serializable
data class RectMm(val x: Double, val y: Double, val width: Double, val height: Double) {
    val right: Double get() = x + width
    val bottom: Double get() = y + height
    val centerX: Double get() = x + width / 2
    val centerY: Double get() = y + height / 2

    fun intersects(other: RectMm, epsilon: Double = EPSILON): Boolean =
        x < other.right - epsilon && other.x < right - epsilon &&
            y < other.bottom - epsilon && other.y < bottom - epsilon

    fun containsRect(other: RectMm, epsilon: Double = EPSILON): Boolean =
        other.x >= x - epsilon && other.y >= y - epsilon &&
            other.right <= right + epsilon && other.bottom <= bottom + epsilon

    fun containsPoint(px: Double, py: Double): Boolean = px in x..right && py in y..bottom

    fun inset(amount: Double): RectMm = RectMm(x + amount, y + amount, width - 2 * amount, height - 2 * amount)

    fun offset(dx: Double, dy: Double): RectMm = copy(x = x + dx, y = y + dy)

    companion object {
        const val EPSILON = 1e-6
    }
}

/** Page margins in millimetres. */
@Serializable
data class Margins(
    val top: Double = 5.0,
    val right: Double = 5.0,
    val bottom: Double = 5.0,
    val left: Double = 5.0,
) {
    companion object {
        fun all(value: Double) = Margins(value, value, value, value)
    }
}
