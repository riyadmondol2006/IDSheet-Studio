package io.github.riyadmondol2006.idsheetstudio.image

import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform

/**
 * Turns a detected face into a crop transform. Pure math, so it can be checked against
 * [CropMath.matrix] on the JVM.
 *
 * The targets are a heuristic approximating ICAO framing for a 35 × 45 mm photo (head about
 * 32–36 mm tall, eyes a little above the middle): the eye midpoint is centred horizontally at
 * [EYE_LINE] of the frame height from the top, and the distance between the eyes is
 * [EYE_DISTANCE] of the frame width. Users should still check the result against the guide.
 */
object FaceAlignMath {
    const val EYE_LINE = 0.42
    /**
     * Tuned on a real portrait: 0.26 made the head ~81 % of the frame height and cut off the crown;
     * 0.235 gives ~75 %, inside ICAO's 71–80 % band and matching the on-screen face guide.
     */
    const val EYE_DISTANCE = 0.235

    /**
     * Transform (rotation 0) placing the eyes of a [srcW] × [srcH] source, given as their midpoint
     * ([eyeX], [eyeY]) and distance [eyeDistance] in source pixels, in a [frameW] × [frameH]
     * frame. Zoom is clamped to [PhotoTransform.MIN_ZOOM]..[PhotoTransform.MAX_ZOOM]; the eye
     * midpoint is still placed exactly. Returns null for degenerate input.
     */
    fun align(
        srcW: Double,
        srcH: Double,
        frameW: Double,
        frameH: Double,
        eyeX: Double,
        eyeY: Double,
        eyeDistance: Double,
    ): PhotoTransform? {
        val values = doubleArrayOf(srcW, srcH, frameW, frameH, eyeX, eyeY, eyeDistance)
        if (values.any { !it.isFinite() } || srcW <= 0 || srcH <= 0 || frameW <= 0 || frameH <= 0 || eyeDistance <= 0) return null
        val cover = CropMath.coverScale(srcW, srcH, frameW, frameH)
        val zoom = (EYE_DISTANCE * frameW / eyeDistance / cover).coerceIn(PhotoTransform.MIN_ZOOM, PhotoTransform.MAX_ZOOM)
        val scale = cover * zoom
        // With rotation 0, CropMath.matrix maps x to frameW / 2 + offsetX * frameW + scale * (x - srcW / 2).
        return PhotoTransform(
            zoom = zoom,
            offsetX = (0.5 * frameW - frameW / 2 - scale * (eyeX - srcW / 2)) / frameW,
            offsetY = (EYE_LINE * frameH - frameH / 2 - scale * (eyeY - srcH / 2)) / frameH,
            rotation = 0.0,
        )
    }
}
