package io.github.riyadmondol2006.idsheetstudio.domain

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutItem
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetComposer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.HorizontalAlign
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.LineStyle
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfInspector
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfWriter
import io.github.riyadmondol2006.idsheetstudio.pdf.SheetPdfEncoder
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class TrimLinesTest {
    private fun copies(size: SizeMm, n: Int) = (0 until n).map { LayoutItem("p#$it", "p", 0, it, size) }

    private fun page(spacing: Double, n: Int = 4, settings: SheetSettings = SheetSettings()) = run {
        val s = settings.copy(spacingMm = spacing, cutGuides = CutGuides.TRIM_LINES, strategy = LayoutStrategy.ROWS, horizontalAlign = HorizontalAlign.START)
        // A 2-column printable area so 4 copies form a 2 x 2 grid.
        val pageSize = SizeMm(10.0 + 35 * 2 + spacing, 297.0)
        val layout = LayoutEngine.layout(copies(SizeMm(35.0, 45.0), n), pageSize, Margins.all(5.0), spacing, LayoutStrategy.ROWS, false)
        Triple(layout, SheetComposer.composePage(0, pageSize, layout.placementsOn(0), s), pageSize)
    }

    @Test fun horizontalCutsSpanThePageAndVerticalCutsStayInTheirStrip() {
        val (_, sheet, size) = page(spacing = 3.0)
        val lines = sheet.ops.filterIsInstance<SheetOp.Line>()
        val vertical = lines.filter { it.x1 == it.x2 }
        val horizontal = lines.filter { it.y1 == it.y2 }
        // Columns at x = 5..40 and 43..78; rows at y = 5..50 and 53..98.
        assertEquals(listOf(5.0, 50.0, 53.0, 98.0), horizontal.map { it.y1 })
        assertTrue(horizontal.all { it.x1 == 0.0 && it.x2 == size.width })
        // Row 1 strip: from the paper's top edge to the first cut below it (row 2 top).
        val row1 = vertical.filter { it.y1 == 0.0 }
        assertEquals(listOf(5.0, 40.0, 43.0, 78.0), row1.map { it.x1 })
        assertTrue(row1.all { it.y2 == 53.0 })
        // Row 2 strip: from the cut above it (row 1 bottom) to 5 mm below the last row.
        val row2 = vertical.filter { it.y1 == 50.0 }
        assertEquals(4, row2.size)
        assertTrue(row2.all { it.y2 == 98.0 + SheetComposer.TRIM_TAIL_MM })
        // Nothing in the empty lower part of the page.
        assertTrue(lines.none { it.x1 == it.x2 && it.y2 > 98.0 + SheetComposer.TRIM_TAIL_MM + 1e-9 })
        assertTrue(lines.all { it.stroke.style == LineStyle.DASHED })
    }

    @Test fun everyPhotoEdgeIsCovered() {
        // Mixed sizes in one row (passport + stamps): each edge of each photo lies on a line.
        val items = copies(SizeMm(35.0, 45.0), 3) + (0 until 4).map { LayoutItem("s#$it", "s", 1, it, SizeMm(20.0, 25.0)) }
        val layout = LayoutEngine.layout(items, SizeMm(210.0, 297.0), Margins.all(5.0), 2.0, LayoutStrategy.ROWS, false)
        val lines = SheetComposer.compose(layout, SheetSettings(cutGuides = CutGuides.TRIM_LINES)).single().ops.filterIsInstance<SheetOp.Line>()
        for (p in layout.placements) {
            val r = p.rect
            fun onV(x: Double) = lines.any { it.x1 == x && it.x2 == x && it.y1 <= r.y + 1e-9 && it.y2 >= r.bottom - 1e-9 }
            fun onH(y: Double) = lines.any { it.y1 == y && it.y2 == y && it.x1 <= r.x + 1e-9 && it.x2 >= r.right - 1e-9 }
            assertTrue("${p.key} edges", onV(r.x) && onV(r.right) && onH(r.y) && onH(r.bottom))
        }
    }

    @Test fun sharedEdgesAreMergedWithoutSpacing() {
        val (_, sheet, _) = page(spacing = 0.0)
        val lines = sheet.ops.filterIsInstance<SheetOp.Line>()
        assertEquals(3 * 2, lines.count { it.x1 == it.x2 }) // 3 shared x-edges in each of the 2 rows
        assertEquals(3, lines.count { it.y1 == it.y2 })
    }

    @Test fun linesAreDrawnUnderneathThePhotos() {
        val (_, sheet, _) = page(spacing = 2.0)
        val firstImage = sheet.ops.indexOfFirst { it is SheetOp.Image }
        val lastLine = sheet.ops.indexOfLast { it is SheetOp.Line }
        assertTrue("lines must come before images", lastLine < firstImage)
    }

    @Test fun guideColourIsUsedAndReachesThePdf() {
        val (layout, sheet, _) = page(spacing = 2.0, settings = SheetSettings(guideColor = 0xFFE53935))
        assertTrue(sheet.ops.filterIsInstance<SheetOp.Line>().all { it.stroke.color == 0xFFE53935 })
        val out = ByteArrayOutputStream()
        PdfWriter(out, compressContent = false).use { w ->
            val ref = w.addJpegImage(10, 10, byteArrayOf(1))
            SheetPdfEncoder.writePage(w, sheet, mapOf("p" to ref))
        }
        val content = PdfInspector.inspect(out.toByteArray()).pages.single().content
        assertEquals(4 + 8, Regex(" l S").findAll(content).count())
        assertTrue(content.contains("0.898 0.2235 0.2078 RG"))
        assertTrue(Regex("""\[[\d.]+ [\d.]+] 0 d""").containsMatchIn(content))
        assertEquals(4, layout.placements.size)
    }

    @Test fun newSheetsDefaultToCuttingLinesAndOldSheetsKeepTheirChoice() {
        assertEquals(CutGuides.TRIM_LINES, SheetSettings().cutGuides)
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString(SheetSettings.serializer(), """{"cutGuides":"CROP_MARKS"}""")
        assertEquals(CutGuides.CROP_MARKS, old.cutGuides)
        assertEquals(0xFF7A7A7A, old.guideColor)
    }
}
