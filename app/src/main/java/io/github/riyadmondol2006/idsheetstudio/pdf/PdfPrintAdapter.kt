package io.github.riyadmondol2006.idsheetstudio.pdf

import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/** Streams an already generated PDF to the Android print framework without re-rendering it. */
class PdfPrintAdapter(
    private val file: File,
    private val pageCount: Int,
) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(file.name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            file.inputStream().use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            if (cancellationSignal.isCanceled) callback.onWriteCancelled() else callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }

    companion object {
        /** Print attributes matching the sheet so print services default to the right paper. */
        fun attributesFor(page: SizeMm): PrintAttributes {
            val mils = { mm: Double -> (mm / 25.4 * 1000).roundToInt() }
            val known = listOf(
                PrintAttributes.MediaSize.ISO_A4, PrintAttributes.MediaSize.ISO_A5,
                PrintAttributes.MediaSize.NA_LETTER, PrintAttributes.MediaSize.NA_LEGAL,
                PrintAttributes.MediaSize.NA_INDEX_4X6, PrintAttributes.MediaSize.NA_INDEX_5X8,
            )
            val portraitW = mils(minOf(page.width, page.height))
            val portraitH = mils(maxOf(page.width, page.height))
            var media = known.firstOrNull { kotlin.math.abs(it.widthMils - portraitW) <= 5 && kotlin.math.abs(it.heightMils - portraitH) <= 5 }
                ?: PrintAttributes.MediaSize("idsheet_custom", "Custom ${page.width.roundToInt()}×${page.height.roundToInt()} mm", portraitW, portraitH)
            if (page.width > page.height) media = media.asLandscape()
            return PrintAttributes.Builder()
                .setMediaSize(media)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build()
        }
    }
}
