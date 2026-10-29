package io.github.riyadmondol2006.idsheetstudio.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.media.FaceDetector
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Eyes of a detected face, in pixels of a [sourceWidth] × [sourceHeight] decode of the photo. */
data class DetectedFace(
    val sourceWidth: Int,
    val sourceHeight: Int,
    val eyeX: Double,
    val eyeY: Double,
    val eyeDistance: Double,
)

/**
 * Finds the most confident face with the platform's offline [FaceDetector]. Runs on the calling
 * thread and is fairly slow, so call it off the main thread.
 */
object FaceAligner {
    /** Longest side the detector sees; plenty for a portrait and keeps detection quick. */
    private const val DETECT_LONGEST = 800
    private const val MAX_FACES = 4

    fun detect(path: String): DetectedFace? {
        val source = BitmapIO.decodeFile(path, DETECT_LONGEST) ?: return null
        try {
            return detect(source)
        } finally {
            source.recycle()
        }
    }

    fun detect(source: Bitmap): DetectedFace? {
        if (source.width <= 0 || source.height <= 0) return null
        val scale = (DETECT_LONGEST.toDouble() / max(source.width, source.height)).coerceAtMost(1.0)
        val scaledW = (source.width * scale).roundToInt().coerceAtLeast(2)
        val scaledH = (source.height * scale).roundToInt().coerceAtLeast(1)
        val sx = scaledW.toDouble() / source.width
        val sy = scaledH.toDouble() / source.height
        // The detector needs RGB_565 and an even width; dropping the last column of an odd width
        // keeps the scale (and so the mapping back) unchanged.
        val detectW = scaledW - scaledW % 2
        val input = createBitmap(detectW, scaledH, Bitmap.Config.RGB_565)
        try {
            Canvas(input).apply {
                // Cut-outs are transparent around the person; white reads better than black.
                drawColor(Color.WHITE)
                drawBitmap(source, Matrix().apply { setScale(sx.toFloat(), sy.toFloat()) }, Paint(Paint.FILTER_BITMAP_FLAG))
            }
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val count = FaceDetector(detectW, scaledH, MAX_FACES).findFaces(input, faces)
            val best = faces.take(count).filterNotNull().maxByOrNull { it.confidence() } ?: return null
            val mid = PointF().also(best::getMidPoint)
            return DetectedFace(
                sourceWidth = source.width,
                sourceHeight = source.height,
                eyeX = mid.x / sx,
                eyeY = mid.y / sy,
                eyeDistance = best.eyesDistance() / ((sx + sy) / 2),
            )
        } finally {
            input.recycle()
        }
    }
}
