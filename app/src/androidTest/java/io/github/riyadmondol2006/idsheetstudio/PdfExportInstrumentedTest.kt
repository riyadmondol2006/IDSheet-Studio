package io.github.riyadmondol2006.idsheetstudio

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.core.mmToPixels
import io.github.riyadmondol2006.idsheetstudio.domain.model.BorderSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.LineStyle
import io.github.riyadmondol2006.idsheetstudio.domain.model.Orientation
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfInspector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** Generates real PDFs on the device and measures them with two independent readers. */
@RunWith(AndroidJUnit4::class)
class PdfExportInstrumentedTest {
    private val container get() = TestPhotos.targetContext.appContainer
    private val created = mutableListOf<String>()

    @After fun cleanUp() = runBlocking<Unit> { created.forEach { container.projects.delete(it) } }

    private suspend fun project(settings: SheetSettings, vararg photos: Pair<String, Int>): String {
        val id = container.projects.createProject("PDF test", settings).also { created += it }
        photos.forEachIndexed { i, (presetId, qty) ->
            container.projects.importPhotos(id, listOf(TestPhotos.synthetic("p$i")), presetId)
            val photo = container.projects.getProject(id)!!.photos.last()
            container.projects.updatePhoto(photo.copy(quantity = qty))
        }
        return id
    }

    @Test fun multiPageMixedSizesAreExact() = runBlocking<Unit> {
        val settings = SheetSettings(
            paperId = PaperSizes.A4.id,
            margins = Margins(6.0, 5.0, 6.0, 5.0),
            spacingMm = 2.0,
            pageBorder = BorderSettings(enabled = true, thicknessMm = 0.5, style = LineStyle.DASHED, insetMm = 2.0),
            photoBorder = BorderSettings(enabled = true, thicknessMm = 0.2, color = 0xFF9E9E9E),
            cutGuides = CutGuides.CROP_MARKS,
        )
        val id = project(settings, "passport_35x45" to 34, "passport_us" to 6, "stamp_20x25" to 12)
        val result = container.exporter.export(container.projects.getProject(id)!!, dpi = 300)
        assertEquals(52, result.copies)
        assertTrue(result.pageCount >= 2)
        assertTrue(result.verification.maxErrorMm < 0.001)

        // Reader 1: our structural parser (exact fractional points).
        val report = PdfInspector.inspect(result.file.readBytes())
        assertEquals(result.pageCount, report.pages.size)
        report.pages.forEach {
            assertEquals(210.0, it.widthMm, 0.001)
            assertEquals(297.0, it.heightMm, 0.001)
        }
        val sizes = report.pages.flatMap { it.images }.map { (it.widthMm * 1000).roundToInt() to (it.heightMm * 1000).roundToInt() }
        assertEquals(34, sizes.count { it == 35000 to 45000 })
        assertEquals(6, sizes.count { it == 50800 to 50800 })
        assertEquals(12, sizes.count { it == 20000 to 25000 })

        // Reader 2: the Android platform PDF renderer must open every page with the same size.
        ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                assertEquals(result.pageCount, renderer.pageCount)
                for (i in 0 until renderer.pageCount) renderer.openPage(i).use { page ->
                    // The platform API exposes whole points and truncates: 595.276 -> 595, 841.890 -> 841.
                    assertEquals(kotlin.math.floor(595.2756).toInt(), page.width)
                    assertEquals(kotlin.math.floor(841.8898).toInt(), page.height)
                }
            }
        }
    }

    @Test fun letterLandscapeAndEmbeddedResolution() = runBlocking<Unit> {
        val settings = SheetSettings(paperId = PaperSizes.LETTER.id, orientation = Orientation.LANDSCAPE, margins = Margins.all(0.25 * 25.4))
        val id = project(settings, "passport_35x45" to 3)
        val result = container.exporter.export(container.projects.getProject(id)!!, dpi = 600)
        val bytes = result.file.readBytes()
        val report = PdfInspector.inspect(bytes)
        assertEquals(792.0, report.pages.single().widthPt, 1e-4)
        assertEquals(612.0, report.pages.single().heightPt, 1e-4)
        // The single unique photo is embedded once at 600 DPI: 35 x 45 mm -> 827 x 1063 px.
        assertEquals(1, report.imageCount)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("/Width ${mmToPixels(35.0, 600)} /Height ${mmToPixels(45.0, 600)}"))
    }

    @Test fun customPaperInInches() = runBlocking<Unit> {
        val settings = SheetSettings(paperId = PaperSizes.CUSTOM_ID, customPaper = SizeMm(4 * 25.4, 6 * 25.4), margins = Margins.all(3.0))
        val id = project(settings, PhotoSizePresets.DEFAULT_ID to 4)
        val result = container.exporter.export(container.projects.getProject(id)!!, dpi = 300)
        val page = PdfInspector.inspect(result.file.readBytes()).pages.single()
        assertEquals(288.0, page.widthPt, 1e-4)
        assertEquals(432.0, page.heightPt, 1e-4)
        assertEquals(4, page.images.size)
    }
}
