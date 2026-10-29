package io.github.riyadmondol2006.idsheetstudio.image

import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure math shared by the interactive editor and the export renderer, so that what users see
 * while cropping is exactly what gets printed.
 */
object CropMath {

    /** Scale that makes a [srcW] × [srcH] image just cover a [dstW] × [dstH] frame. */
    fun coverScale(srcW: Double, srcH: Double, dstW: Double, dstH: Double): Double =
        max(dstW / srcW, dstH / srcH)

    /**
     * Affine matrix mapping source pixels to frame pixels, as
     * `[scaleX, skewX, transX, skewY, scaleY, transY]` (android.graphics.Matrix order):
     * `x' = m0 * x + m1 * y + m2`, `y' = m3 * x + m4 * y + m5`.
     */
    fun matrix(srcW: Double, srcH: Double, dstW: Double, dstH: Double, t: PhotoTransform): DoubleArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val s = coverScale(srcW, srcH, dstW, dstH) * t.zoom
        val rad = t.rotation * PI / 180.0
        val c = cos(rad) * s
        val sn = sin(rad) * s
        val cx = dstW / 2 + t.offsetX * dstW
        val cy = dstH / 2 + t.offsetY * dstH
        // translate(-src/2) -> scale -> rotate -> translate(centre)
        val tx = cx - (c * srcW / 2 - sn * srcH / 2)
        val ty = cy - (sn * srcW / 2 + c * srcH / 2)
        return doubleArrayOf(c, -sn, tx, sn, c, ty)
    }

    fun apply(m: DoubleArray, x: Double, y: Double): Pair<Double, Double> =
        (m[0] * x + m[1] * y + m[2]) to (m[3] * x + m[4] * y + m[5])

    /**
     * Updates a transform for a pinch/drag/rotate gesture expressed in frame pixels.
     * The point under the gesture centroid stays fixed while zooming and rotating.
     */
    fun applyGesture(
        t: PhotoTransform,
        frameW: Double,
        frameH: Double,
        centroidX: Double,
        centroidY: Double,
        panX: Double,
        panY: Double,
        zoomChange: Double,
        rotationChange: Double,
    ): PhotoTransform {
        if (frameW <= 0 || frameH <= 0 || !zoomChange.isFinite() || zoomChange <= 0 ||
            !panX.isFinite() || !panY.isFinite() || !rotationChange.isFinite()
        ) {
            return t
        }
        val newZoom = (t.zoom * zoomChange).coerceIn(PhotoTransform.MIN_ZOOM, PhotoTransform.MAX_ZOOM)
        val effectiveZoom = newZoom / t.zoom
        val cx = frameW / 2 + t.offsetX * frameW
        val cy = frameH / 2 + t.offsetY * frameH
        val rad = rotationChange * PI / 180.0
        val dx = cx - centroidX
        val dy = cy - centroidY
        val rx = (dx * cos(rad) - dy * sin(rad)) * effectiveZoom
        val ry = (dx * sin(rad) + dy * cos(rad)) * effectiveZoom
        val newCx = centroidX + rx + panX
        val newCy = centroidY + ry + panY
        return t.copy(
            zoom = newZoom,
            rotation = normalizeDegrees(t.rotation + rotationChange),
            offsetX = (newCx - frameW / 2) / frameW,
            offsetY = (newCy - frameH / 2) / frameH,
        )
    }

    fun normalizeDegrees(deg: Double): Double {
        var d = deg % 360.0
        if (d > 180) d -= 360
        if (d <= -180) d += 360
        return d
    }
}
