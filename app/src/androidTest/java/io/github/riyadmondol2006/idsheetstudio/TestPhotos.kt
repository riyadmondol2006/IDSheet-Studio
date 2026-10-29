package io.github.riyadmondol2006.idsheetstudio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** Test images: a synthetic portrait (generated) and a public-domain photo from test assets. */
object TestPhotos {
    val targetContext: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun synthetic(name: String, width: Int = 1200, height: Int = 1600, color: Int = Color.rgb(70, 110, 200)): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(color)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.rgb(240, 200, 170) }
        canvas.drawCircle(width / 2f, height * 0.4f, width * 0.22f, paint)
        paint.color = Color.DKGRAY
        canvas.drawRect(width * 0.2f, height * 0.7f, width * 0.8f, height.toFloat(), paint)
        val file = File(targetContext.cacheDir, "$name.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return Uri.fromFile(file)
    }

    /** Public-domain US Congress official portrait (Wikimedia Commons), used for remove.bg tests. */
    fun realPortrait(): Uri {
        val file = File(targetContext.cacheDir, "portrait.jpg")
        InstrumentationRegistry.getInstrumentation().context.assets.open("portrait.jpg").use { input ->
            file.outputStream().use { input.copyTo(it) }
        }
        return Uri.fromFile(file)
    }
}
