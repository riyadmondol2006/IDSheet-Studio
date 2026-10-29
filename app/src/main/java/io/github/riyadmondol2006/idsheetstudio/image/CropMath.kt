package io.github.riyadmondol2006.idsheetstudio.image

import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Pure math shared by the interactive editor and the export renderer, so that what users see
 * while cropping is exactly what gets printed.
 */
object CropMath {

    /**
     * Scale that makes a [srcW] × [srcH] image, rotated by [rotationDeg] around its centre, just
     * cover a [dstW] × [dstH] frame. Seen from the image, the frame's bounding box is
     * (W|cos θ| + H|sin θ|) × (W|sin θ| + H|cos θ|), and that box must fit inside the scaled image.
     * At θ = 0 this is the plain `max(W / srcW, H / srcH)`.
     */
    fun coverScale(srcW: Double, srcH: Double, dstW: Double, dstH: Double, rotationDeg: Double = 0.0): Double {
        val rad = rotationDeg * PI / 180.0
        val c = abs(cos(rad))
        val s = abs(sin(rad))
        return max((dstW * c + dstH * s) / srcW, (dstW * s + dstH * c) / srcH)
    }

    /**
     * Affine matrix mapping source pixels to frame pixels, as
     * `[scaleX, skewX, transX, skewY, scaleY, transY]` (android.graphics.Matrix order):
     * `x' = m0 * x + m1 * y + m2`, `y' = m3 * x + m4 * y + m5`.
     */
    fun matrix(srcW: Double, srcH: Double, dstW: Double, dstH: Double, t: PhotoTransform): DoubleArray {
        if (srcW <= 0 || srcH <= 0 || dstW <= 0 || dstH <= 0) return doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0)
        val s = coverScale(srcW, srcH, dstW, dstH, t.rotation) * t.zoom
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
     *
     * Zoom is relative to the rotation-aware cover scale, so twisting also changes the absolute
     * scale; pass the source size ([srcW] × [srcH], any resolution) so that change is applied
     * around the centroid too. Without it, the cover scale is assumed not to change.
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
        srcW: Double = 0.0,
        srcH: Double = 0.0,
    ): PhotoTransform {
        if (frameW <= 0 || frameH <= 0 || !zoomChange.isFinite() || zoomChange <= 0 ||
            !panX.isFinite() || !panY.isFinite() || !rotationChange.isFinite()
        ) {
            return t
        }
        val newZoom = (t.zoom * zoomChange).coerceIn(PhotoTransform.MIN_ZOOM, PhotoTransform.MAX_ZOOM)
        val newRotation = normalizeDegrees(t.rotation + rotationChange)
        val coverChange = if (srcW > 0 && srcH > 0) {
            coverScale(srcW, srcH, frameW, frameH, newRotation) / coverScale(srcW, srcH, frameW, frameH, t.rotation)
        } else {
            1.0
        }
        val effectiveZoom = newZoom / t.zoom * coverChange
        val cx = frameW / 2 + t.offsetX * frameW
        val cy = frameH / 2 + t.offsetY * frameH
        val rad = rotationChange * PI / 180.0
        val dx = cx - centroidX
        val dy = cy - centroidY
        val rx = (dx * cos(rad) - dy * sin(rad)) * effectiveZoom
        val ry = (dx * sin(rad) + dy * cos(rad)) * effectiveZoom
        var newCx = centroidX + rx + panX
        var newCy = centroidY + ry + panY
        // Never let the photo be dragged out of the frame: the frame centre must stay on the
        // (rotated) photo. Without a known source size, allow at most one frame of travel.
        val (maxDx, maxDy) = if (srcW > 0 && srcH > 0) {
            val s = coverScale(srcW, srcH, frameW, frameH, newRotation) * newZoom
            val r = newRotation * PI / 180.0
            val c = abs(cos(r))
            val sn = abs(sin(r))
            (s * (srcW * c + srcH * sn) / 2) to (s * (srcW * sn + srcH * c) / 2)
        } else {
            frameW to frameH
        }
        newCx = newCx.coerceIn(frameW / 2 - maxDx, frameW / 2 + maxDx)
        newCy = newCy.coerceIn(frameH / 2 - maxDy, frameH / 2 + maxDy)
        return t.copy(
            zoom = newZoom,
            rotation = newRotation,
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
