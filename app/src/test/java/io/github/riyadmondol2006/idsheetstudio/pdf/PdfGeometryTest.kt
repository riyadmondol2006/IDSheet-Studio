package io.github.riyadmondol2006.idsheetstudio.pdf

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.core.mmToPoints
import io.github.riyadmondol2006.idsheetstudio.core.pointsToMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutItem
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetComposer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.model.BorderSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.LineStyle
import io.github.riyadmondol2006.idsheetstudio.domain.model.Orientation
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * End-to-end geometry checks: layout -> sheet ops -> PDF bytes -> parsed back. Proves the file
 * contains exact page boxes and that every photo is placed at its exact physical size.
 */
class PdfGeometryTest {

    // A tiny but valid baseline JPEG is not needed: the writer embeds bytes verbatim.
    private val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())

    private fun render(settings: SheetSettings, items: List<LayoutItem>, compress: Boolean = true): Pair<PdfInspector.Report, List<io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetPage>> {
        val layout = LayoutEngine.layout(items, settings.pageSize, settings.margins, settings.spacingMm, settings.strategy, settings.allowRotation)
        assertEquals(emptyList<String>(), LayoutEngine.validate(layout, items))
        val pages = SheetComposer.compose(layout, settings)
        val out = ByteArrayOutputStream()
        PdfWriter(out, PdfInfo(title = "Test sheet", creationTime = 0), compressContent = compress).use { writer ->
            val refs = items.map { it.photoId }.distinct().associateWith { writer.addJpegImage(100, 130, fakeJpeg) }
            pages.forEach { SheetPdfEncoder.writePage(writer, it, refs) }
        }
        return PdfInspector.inspect(out.toByteArray()) to pages
    }

    private fun items(vararg groups: Pair<SizeMm, Int>): List<LayoutItem> {
        var order = 0
        return groups.flatMapIndexed { g, (size, qty) -> (0 until qty).map { c -> LayoutItem("p$g#$c", "p$g", order++, c, size) } }
    }

    @Test fun standardPaperSizesAreExact() {
        val expectations = mapOf(
            PaperSizes.A4 to (595.2756 to 841.8898),
            PaperSizes.A5 to (419.5276 to 595.2756),
            PaperSizes.LETTER to (612.0 to 792.0),
            PaperSizes.LEGAL to (612.0 to 1008.0),
        )
        for ((paper, pts) in expectations) {
            for (orientation in Orientation.entries) {
                val settings = SheetSettings(paperId = paper.id, orientation = orientation)
                val (report, _) = render(settings, items(SizeMm(35.0, 45.0) to 1))
                val page = report.pages.single()
                val (w, h) = if (orientation == Orientation.PORTRAIT) pts else pts.second to pts.first
                assertEquals("${paper.name} $orientation width", w, page.widthPt, 1e-4)
                assertEquals("${paper.name} $orientation height", h, page.heightPt, 1e-4)
                assertEquals(paper.size.let { if (orientation == Orientation.PORTRAIT) it.width else it.height }, page.widthMm, 1e-3)
            }
        }
    }

    @Test fun customPaperIsExact() {
        val settings = SheetSettings(paperId = PaperSizes.CUSTOM_ID, customPaper = SizeMm(101.6, 152.4))
        val (report, _) = render(settings, items(SizeMm(35.0, 45.0) to 4))
        assertEquals(288.0, report.pages.single().widthPt, 1e-6)
        assertEquals(432.0, report.pages.single().heightPt, 1e-6)
    }

    @Test fun everyPhotoHasExactSizeAndPosition() {
        val settings = SheetSettings(margins = Margins(8.0, 6.0, 8.0, 6.0), spacingMm = 2.5, strategy = LayoutStrategy.AUTO)
        val list = items(SizeMm(35.0, 45.0) to 13, SizeMm(50.8, 50.8) to 7, SizeMm(20.0, 25.0) to 18)
        for (compress in listOf(true, false)) {
            val (report, pages) = render(settings, list, compress)
            assertTrue(report.xrefValid)
            assertEquals(3, report.imageCount)
            assertEquals(list.size, report.pages.sumOf { it.images.size })
            pages.zip(report.pages).forEach { (page, parsed) ->
                val ops = page.ops.filterIsInstance<SheetOp.Image>()
                assertEquals(ops.size, parsed.images.size)
                ops.zip(parsed.images).forEach { (op, draw) ->
                    assertEquals(op.rect.width, draw.widthMm, 1e-3)
                    assertEquals(op.rect.height, draw.heightMm, 1e-3)
                    assertEquals(op.rect.x, pointsToMm(draw.xPt), 1e-3)
                    assertEquals(op.rect.y, page.size.height - pointsToMm(draw.yPt + draw.heightPt), 1e-3)
                }
            }
        }
    }

    @Test fun rotatedPhotosKeepPhysicalSize() {
        val settings = SheetSettings(allowRotation = true, margins = Margins.all(5.0))
        val (report, pages) = render(settings, items(SizeMm(250.0, 60.0) to 2))
        val draws = report.pages.flatMap { it.images }
        assertEquals(2, draws.size)
        draws.forEach {
            assertTrue(it.rotated)
            assertEquals(60.0, it.widthMm, 1e-3)
            assertEquals(250.0, it.heightMm, 1e-3)
        }
        assertTrue(pages.flatMap { it.ops }.filterIsInstance<SheetOp.Image>().all { it.rotated })
    }

    @Test fun bordersAndGuidesAreEmitted() {
        val settings = SheetSettings(
            pageBorder = BorderSettings(enabled = true, thicknessMm = 1.0, color = 0xFFFF0000, style = LineStyle.DASHED, insetMm = 4.0),
            photoBorder = BorderSettings(enabled = true, thicknessMm = 0.5, color = 0xFF000000, style = LineStyle.SOLID, insetMm = 0.0),
            cutGuides = CutGuides.CROP_MARKS,
        )
        val (report, pages) = render(settings, items(SizeMm(35.0, 45.0) to 2), compress = false)
        val content = report.pages.single().content
        assertTrue("red stroke colour", content.contains("1 0 0 RG"))
        assertTrue("dash pattern", Regex("""\[\d+(\.\d+)? \d+(\.\d+)?] 0 d""").containsMatchIn(content))
        // Page border: centre line at inset + thickness/2 = 4.5 mm from each edge.
        val pageBorder = pages.single().ops.filterIsInstance<SheetOp.StrokeRect>().last()
        assertEquals(4.5, pageBorder.rect.x, 1e-9)
        assertEquals(210.0 - 9.0, pageBorder.rect.width, 1e-9)
        assertTrue(content.contains("${PdfWriter.num(mmToPoints(4.5))} ${PdfWriter.num(mmToPoints(297.0) - mmToPoints(292.5))} ${PdfWriter.num(mmToPoints(201.0))} ${PdfWriter.num(mmToPoints(288.0))} re S"))
        // Photo borders lie fully inside the photo (centre line inset by half the thickness).
        val photo = pages.single().ops.filterIsInstance<SheetOp.Image>().first().rect
        val border = pages.single().ops.filterIsInstance<SheetOp.StrokeRect>().first().rect
        assertEquals(photo.x + 0.25, border.x, 1e-9)
        assertEquals(photo.width - 0.5, border.width, 1e-9)
        // 8 crop-mark lines per photo, all within the page.
        val lines = pages.single().ops.filterIsInstance<SheetOp.Line>()
        assertEquals(16, lines.size)
        assertTrue(lines.all { it.x1 in 0.0..210.0 && it.x2 in 0.0..210.0 && it.y1 in 0.0..297.0 && it.y2 in 0.0..297.0 })
    }

    @Test fun pdfStructureIsWellFormed() {
        val out = ByteArrayOutputStream()
        PdfWriter(out, PdfInfo(title = "Ünïcode (title)", creationTime = 0)).use { w ->
            w.addPage(100.0, 200.0) {}
        }
        val text = String(out.toByteArray(), Charsets.ISO_8859_1)
        assertTrue(text.startsWith("%PDF-1.4\n"))
        assertTrue(text.contains("/Title <FEFF"))
        assertTrue(text.contains("/PrintScaling /None"))
        val report = PdfInspector.inspect(out.toByteArray())
        assertTrue(report.xrefValid)
        assertEquals(1, report.pages.size)
        assertEquals("(a\\(b\\)c\\\\)", PdfWriter.pdfString("a(b)c\\"))
    }
}
