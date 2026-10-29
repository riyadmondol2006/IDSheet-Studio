package io.github.riyadmondol2006.idsheetstudio.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.util.LruCache
import androidx.core.graphics.createBitmap
import io.github.riyadmondol2006.idsheetstudio.core.mmToPixels
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import java.io.ByteArrayOutputStream
import kotlin.math.ceil
import kotlin.math.max

/**
 * Renders the final, cropped photo exactly as it will be printed: background colour, the active
 * source (original or background-removed cut-out) and the user's zoom/offset/rotation.
 */
class PhotoRenderer {

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    /** Renders [photo] into a [width] × [height] pixel bitmap. */
    fun render(photo: PhotoItem, width: Int, height: Int): Bitmap? {
        val (srcW, srcH) = BitmapIO.readSize(photo.activePath) ?: return null
        val cover = CropMath.coverScale(srcW.toDouble(), srcH.toDouble(), width.toDouble(), height.toDouble())
        val effectiveScale = cover * photo.transform.zoom
        // Decode only as many source pixels as the output needs.
        val neededLongest = ceil(max(srcW, srcH) * effectiveScale.coerceAtMost(1.0)).toInt()
        val source = BitmapIO.decodeFile(photo.activePath, neededLongest) ?: return null
        try {
            return renderFrom(source, photo, width, height)
        } finally {
            source.recycle()
        }
    }

    fun renderFrom(source: Bitmap, photo: PhotoItem, width: Int, height: Int): Bitmap {
        val out = createBitmap(width, height)
        val canvas = Canvas(out)
        canvas.drawColor(photo.backgroundColor.toInt())
        val m = CropMath.matrix(source.width.toDouble(), source.height.toDouble(), width.toDouble(), height.toDouble(), photo.transform)
        val matrix = Matrix().apply { setValues(floatArrayOf(m[0].toFloat(), m[1].toFloat(), m[2].toFloat(), m[3].toFloat(), m[4].toFloat(), m[5].toFloat(), 0f, 0f, 1f)) }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        return out
    }

    /** Cached preview rendering whose longest side is [longestPx]. */
    fun preview(photo: PhotoItem, longestPx: Int): Bitmap? {
        val aspect = photo.size.width / photo.size.height
        val (w, h) = if (aspect >= 1) longestPx to max(1, (longestPx / aspect).toInt()) else max(1, (longestPx * aspect).toInt()) to longestPx
        val key = "${photo.id}:${photo.activePath}:${photo.transform}:${photo.size}:${photo.backgroundColor}:${w}x$h"
        cache.get(key)?.let { return it }
        val bitmap = render(photo, w, h) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    /**
     * Renders [photo] at its physical size and [dpi], encoded as a high-quality JPEG. Very large
     * photos are capped at [MAX_PRINT_PIXELS] (the imported source never has more detail than
     * that), which keeps memory bounded; the physical size in the PDF is unaffected.
     */
    fun renderJpegForPrint(photo: PhotoItem, dpi: Int, quality: Int = 95): RenderedJpeg? {
        val effectiveDpi = effectiveDpi(photo.size.width, photo.size.height, dpi)
        val w = mmToPixels(photo.size.width, effectiveDpi)
        val h = mmToPixels(photo.size.height, effectiveDpi)
        val bitmap = render(photo, w, h) ?: return null
        try {
            val out = ByteArrayOutputStream(w * h / 4)
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            return RenderedJpeg(w, h, out.toByteArray())
        } finally {
            bitmap.recycle()
        }
    }

    fun clear() = cache.evictAll()
}

/** Largest bitmap rendered for print: 24 MP (about 96 MB while rendering). */
const val MAX_PRINT_PIXELS = 24_000_000L

fun effectiveDpi(widthMm: Double, heightMm: Double, dpi: Int): Int {
    val pixels = (widthMm / 25.4 * dpi) * (heightMm / 25.4 * dpi)
    if (pixels <= MAX_PRINT_PIXELS) return dpi
    return kotlin.math.floor(dpi * kotlin.math.sqrt(MAX_PRINT_PIXELS / pixels)).toInt().coerceAtLeast(72)
}

class RenderedJpeg(val width: Int, val height: Int, val bytes: ByteArray)
