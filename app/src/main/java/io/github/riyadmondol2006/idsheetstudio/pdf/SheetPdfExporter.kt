package io.github.riyadmondol2006.idsheetstudio.pdf

import android.content.Context
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.core.pointsToMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetComposer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetPage
import io.github.riyadmondol2006.idsheetstudio.domain.model.Project
import io.github.riyadmondol2006.idsheetstudio.image.PhotoRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

import kotlin.math.abs

data class ExportResult(
    val file: File,
    val pageCount: Int,
    val pageSize: SizeMm,
    val copies: Int,
    val missingCopies: Int,
    val dpi: Int,
    /** Result of reading the written file back: every page box and photo checked. */
    val verification: PdfVerification,
)

data class PdfVerification(val pagesChecked: Int, val photosChecked: Int, val maxErrorMm: Double)

class SheetPdfExporter(
    private val context: Context,
    private val renderer: PhotoRenderer,
) {
    /** Exports [project] to a print-ready PDF in the app cache and returns its metadata. */
    suspend fun export(project: Project, dpi: Int, onProgress: (Float) -> Unit = {}): ExportResult = withContext(Dispatchers.Default) {
        val layout = LayoutEngine.layout(project.photos, project.settings)
        if (layout.pageCount == 0) {
            throw IOException(
                when {
                    !layout.isValidPrintableArea -> "The margins leave no printable area on this paper. Reduce them in the Paper tab."
                    layout.unplaced.isNotEmpty() -> "None of the photos fit inside the margins on this paper. Use larger paper, smaller margins or allow rotation."
                    else -> "Nothing to export. Add photos with a quantity above zero."
                },
            )
        }
        val problems = LayoutEngine.validate(layout, LayoutEngine.expand(project.photos))
        check(problems.isEmpty()) { "Invalid layout: ${problems.first()}" }
        val pages = SheetComposer.compose(layout, project.settings)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val safeName = project.name.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "IDSheet" }.take(60)
        // The id suffix keeps sheets with the same (or non-Latin) name from overwriting each other.
        val fileName = "$safeName-${project.id.take(8)}"
        val file = File(dir, "$fileName.pdf")
        val tmp = File(dir, "$fileName.pdf.part")

        val usedIds = pages.flatMap { p -> p.ops.filterIsInstance<SheetOp.Image>().map { it.photoId } }.distinct()
        val photos = project.photos.associateBy { it.id }
        val totalSteps = usedIds.size + pages.size
        var done = 0
        val refs = HashMap<String, PdfWriter.ImageRef>()

        withContext(Dispatchers.IO) {
            tmp.outputStream().buffered(256 * 1024).use { stream ->
                val writer = PdfWriter(stream, PdfInfo(title = project.name))
                for (id in usedIds) {
                    currentCoroutineContext().ensureActive()
                    val photo = photos.getValue(id)
                    val jpeg = renderer.renderJpegForPrint(photo, dpi)
                        ?: throw IOException("Could not read the image for \"${photo.label}\".")
                    refs[id] = writer.addJpegImage(jpeg.width, jpeg.height, jpeg.bytes)
                    onProgress(++done / totalSteps.toFloat())
                }
                for (page in pages) {
                    currentCoroutineContext().ensureActive()
                    SheetPdfEncoder.writePage(writer, page, refs)
                    onProgress(++done / totalSteps.toFloat())
                }
                writer.close()
            }
            if (file.exists()) file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not save the PDF.")
        }
        val verification = withContext(Dispatchers.IO) { verify(file, pages, refs) }
        ExportResult(
            file = file,
            verification = verification,
            pageCount = pages.size,
            pageSize = layout.pageSize,
            copies = layout.placements.size,
            missingCopies = layout.unplaced.size,
            dpi = dpi,
        )
    }

    /**
     * Reads the finished file back and checks that every page has the requested size and every
     * photo is drawn at its exact physical size and position (tolerance 0.001 mm).
     */
    private fun verify(file: File, pages: List<SheetPage>, refs: Map<String, PdfWriter.ImageRef>): PdfVerification {
        val report = PdfInspector.inspect(file.readBytes())
        if (!report.xrefValid) throw IOException("PDF verification failed: corrupt cross-reference table.")
        if (report.pages.size != pages.size) throw IOException("PDF verification failed: page count mismatch.")
        var maxError = 0.0
        var photos = 0
        pages.zip(report.pages).forEach { (expected, actual) ->
            maxError = maxOf(maxError, abs(actual.widthMm - expected.size.width), abs(actual.heightMm - expected.size.height))
            val expectedImages = expected.ops.filterIsInstance<SheetOp.Image>()
            if (expectedImages.size != actual.images.size) throw IOException("PDF verification failed: photo count mismatch.")
            expectedImages.zip(actual.images).forEach { (op, draw) ->
                if (refs[op.photoId]?.name != draw.name) throw IOException("PDF verification failed: wrong image order.")
                val x = pointsToMm(draw.xPt)
                val yTop = expected.size.height - pointsToMm(draw.yPt + draw.heightPt)
                maxError = maxOf(
                    maxError,
                    abs(draw.widthMm - op.rect.width), abs(draw.heightMm - op.rect.height),
                    abs(x - op.rect.x), abs(yTop - op.rect.y),
                )
                photos++
            }
        }
        if (maxError > 0.001) throw IOException("PDF verification failed: geometry off by $maxError mm.")
        return PdfVerification(report.pages.size, photos, maxError)
    }
}
