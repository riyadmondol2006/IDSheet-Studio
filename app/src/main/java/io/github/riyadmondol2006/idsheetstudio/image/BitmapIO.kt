package io.github.riyadmondol2006.idsheetstudio.image

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

data class ImportedImage(val width: Int, val height: Int)

/** Decoding helpers that respect EXIF orientation and keep memory bounded. */
object BitmapIO {
    /** Longest side stored for imported photos. Large enough for 600 DPI output of a cropped face. */
    const val IMPORT_MAX_DIMENSION = 3200

    /**
     * Copies an image from [uri] into [dest] as an upright JPEG no larger than [maxDimension].
     * Supports every format the platform can decode (JPEG, PNG, WebP, HEIF/HEIC, AVIF on newer OS).
     */
    @Throws(IOException::class)
    fun import(resolver: ContentResolver, uri: Uri, dest: File, maxDimension: Int = IMPORT_MAX_DIMENSION): ImportedImage {
        val bitmap = decodeUpright(resolver, uri, maxDimension)
        try {
            writeJpeg(bitmap, dest, 95)
            return ImportedImage(bitmap.width, bitmap.height)
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeUpright(resolver: ContentResolver, uri: Uri, maxDimension: Int): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(resolver, uri)
            return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = false
                val w = info.size.width
                val h = info.size.height
                val longest = max(w, h)
                if (longest > maxDimension) {
                    val scale = maxDimension.toDouble() / longest
                    decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
                }
            }.let { ensureArgb(it) }
        }
        // API 26-27: BitmapFactory + manual EXIF rotation.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw IOException("Cannot open image")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException("Unsupported image format")
        val options = BitmapFactory.Options().apply {
            // Smallest power-of-two that brings the longest side to at most 1.28x the target
            // (e.g. <= 4096 px for 3200), keeping the decode + EXIF-rotation peak bounded.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDimension * 1.28) sample *= 2
            inSampleSize = sample
        }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("Cannot decode image")
        val orientation = resolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL
        return scaleToMax(applyExif(decoded, orientation), maxDimension)
    }

    private fun ensureArgb(bitmap: Bitmap): Bitmap {
        if (bitmap.config == Bitmap.Config.ARGB_8888) return bitmap
        val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        bitmap.recycle()
        return copy
    }

    private fun applyExif(bitmap: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun scaleToMax(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toDouble() / longest
        val scaled = bitmap.scale(
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
        )
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    /** Largest power-of-two sample size that keeps the longest side at least [target]. */
    fun sampleSizeFor(longest: Int, target: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Decodes a stored file so its longest side is at least [minLongest] (power-of-two sampling). */
    fun decodeFile(path: String, minLongest: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(max(bounds.outWidth, bounds.outHeight), minLongest.coerceAtLeast(1))
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    fun readSize(path: String): Pair<Int, Int>? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        return if (bounds.outWidth > 0) bounds.outWidth to bounds.outHeight else null
    }

    /** Re-encodes [path] as a JPEG whose longest side is at most [maxDimension], for uploading. */
    fun encodeForUpload(path: String, maxDimension: Int): ByteArray {
        val bitmap = decodeFile(path, maxDimension) ?: throw IOException("Cannot read photo")
        val scaled = scaleToMax(bitmap, maxDimension)
        try {
            val out = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 92, out)
            return out.toByteArray()
        } finally {
            scaled.recycle()
        }
    }

    fun writeJpeg(bitmap: Bitmap, dest: File, quality: Int) {
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) throw IOException("JPEG encoding failed")
        }
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }
}
