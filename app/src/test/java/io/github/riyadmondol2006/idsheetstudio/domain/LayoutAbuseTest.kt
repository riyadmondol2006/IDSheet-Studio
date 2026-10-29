package io.github.riyadmondol2006.idsheetstudio.domain

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.RectMm
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutItem
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetComposer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.model.BorderSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.ManualPosition
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.system.measureTimeMillis

/** Inputs a careless or curious user can produce through the UI. */
class LayoutAbuseTest {
    private val a4 = PaperSizes.A4.size

    private fun copies(size: SizeMm, qty: Int, id: String = "p") =
        (0 until qty).map { LayoutItem("$id#$it", id, 0, it, size) }

    private fun layout(items: List<LayoutItem>, strategy: LayoutStrategy = LayoutStrategy.AUTO, margins: Margins = Margins.all(5.0), spacing: Double = 2.0, page: SizeMm = a4, rotate: Boolean = false, manual: Map<String, ManualPosition> = emptyMap()) =
        LayoutEngine.layout(items, page, margins, spacing, strategy, rotate, manual).also {
            assertEquals(emptyList<String>(), LayoutEngine.validate(it, items))
            assertEquals("every copy is placed or reported", items.size, it.placements.size + it.unplaced.size)
        }

    @Test fun maximumQuantityOfTinyPhotosIsFastAndComplete() {
        // 500 copies (the UI maximum) of a 15 x 20 mm stamp, all strategies, under a time budget.
        val items = copies(SizeMm(15.0, 20.0), 500)
        for (strategy in LayoutStrategy.entries) {
            var pages = 0
            val ms = measureTimeMillis { pages = layout(items, strategy).pageCount }
            assertTrue("$strategy took $ms ms", ms < 5_000)
            assertTrue(pages in 4..6)
        }
    }

    @Test fun manyDistinctPhotosAtMaximumQuantity() {
        val items = (0 until 12).flatMap { i -> copies(SizeMm(20.0 + i * 3, 25.0 + i * 4), 40, "p$i") }
        val ms = measureTimeMillis { layout(items, LayoutStrategy.AUTO, rotate = true) }
        assertTrue("took $ms ms", ms < 15_000)
    }

    @Test fun exactFitWithZeroSpacingHasNoFloatingPointLoss() {
        // 10 x 20 mm across a 200 mm printable width must fit exactly, 0.1 mm values included.
        val r = layout(copies(SizeMm(20.0, 28.7), 100), LayoutStrategy.ROWS, spacing = 0.0)
        assertEquals(1, r.pageCount)
        val tricky = layout(copies(SizeMm(19.9, 28.7), 10) + copies(SizeMm(0.1 * 3, 28.7), 10, "q"), LayoutStrategy.ROWS, spacing = 0.0)
        assertEquals(0, tricky.unplaced.size)
    }

    @Test fun photoExactlyAsLargeAsPrintableArea() {
        val r = layout(copies(SizeMm(200.0, 287.0), 3))
        assertEquals(3, r.pageCount)
        assertEquals(0, r.unplaced.size)
    }

    @Test fun spacingLargerThanPageStillPlacesOnePerPage() {
        val r = layout(copies(SizeMm(35.0, 45.0), 3), spacing = 500.0)
        assertEquals(3, r.pageCount)
    }

    @Test fun negativeSpacingIsTreatedAsZero() {
        val r = LayoutEngine.layout(copies(SizeMm(35.0, 45.0), 30), a4, Margins.all(5.0), -10.0, LayoutStrategy.ROWS, false)
        assertEquals(0.0, r.spacingMm, 0.0)
        assertEquals(emptyList<String>(), LayoutEngine.validate(r, copies(SizeMm(35.0, 45.0), 30)))
    }

    @Test fun marginsLargerThanPageReportEverythingUnplaced() {
        val items = copies(SizeMm(35.0, 45.0), 4)
        for (m in listOf(Margins.all(105.0), Margins(top = 300.0, right = 0.0, bottom = 0.0, left = 0.0), Margins(0.0, 210.0, 0.0, 0.0))) {
            val r = LayoutEngine.layout(items, a4, m, 2.0, LayoutStrategy.AUTO, false)
            assertEquals(0, r.pageCount)
            assertEquals(4, r.unplaced.size)
        }
    }

    @Test fun tinyCustomPaper() {
        val r = layout(copies(SizeMm(35.0, 45.0), 2), page = SizeMm(20.0, 20.0), margins = Margins.all(0.0))
        assertEquals(2, r.unplaced.size)
        val fits = layout(copies(SizeMm(15.0, 20.0), 2), page = SizeMm(20.0, 20.0), margins = Margins.all(0.0))
        assertEquals(2, fits.pageCount)
    }

    @Test fun manualPositionsForDeletedPhotosOrMissingPagesAreIgnored() {
        val items = copies(SizeMm(35.0, 45.0), 2)
        val manual = mapOf(
            "ghost#0" to ManualPosition(0, 50.0, 50.0),
            "p#1" to ManualPosition(7, 50.0, 50.0),
            "p#0" to ManualPosition(0, -20.0, 1e9),
        )
        val r = layout(items, manual = manual)
        assertTrue(r.placements.none { it.manual })
    }

    @Test fun twoCopiesDraggedOntoTheSameSpotOnlyFirstWins() {
        val items = copies(SizeMm(35.0, 45.0), 3)
        val manual = mapOf("p#1" to ManualPosition(0, 100.0, 200.0), "p#2" to ManualPosition(0, 110.0, 210.0))
        val r = layout(items, LayoutStrategy.ROWS, manual = manual)
        assertEquals(1, r.placements.count { it.manual })
    }

    @Test fun absurdBordersNeverEscapeTheirBounds() {
        val photo = RectMm(10.0, 10.0, 35.0, 45.0)
        assertNull(SheetComposer.borderOp(photo, BorderSettings(enabled = true, thicknessMm = 40.0)))
        assertNull(SheetComposer.borderOp(photo, BorderSettings(enabled = true, insetMm = 30.0)))
        assertNull(SheetComposer.borderOp(photo, BorderSettings(enabled = true, thicknessMm = 0.0)))
        val ok = SheetComposer.borderOp(photo, BorderSettings(enabled = true, thicknessMm = 2.0, insetMm = -5.0))!!
        // Negative inset is clamped: the stroke still lies fully inside the photo.
        assertEquals(11.0, ok.rect.x, 1e-9)
        assertTrue(photo.containsRect(ok.rect.inset(-1.0)))
    }

    @Test fun cropMarksStayOnThePageForPhotosTouchingTheEdge() {
        val settings = SheetSettings(margins = Margins.all(0.0), cutGuides = CutGuides.CROP_MARKS, spacingMm = 0.0)
        val items = copies(SizeMm(35.0, 45.0), 40)
        val r = layout(items, margins = settings.margins, spacing = 0.0)
        SheetComposer.compose(r, settings).flatMap { it.ops }.filterIsInstance<SheetOp.Line>().forEach {
            assertTrue(it.x1 in 0.0..210.0 && it.x2 in 0.0..210.0 && it.y1 in 0.0..297.0 && it.y2 in 0.0..297.0)
        }
    }

    @Test fun dragBAwayThenAIntoBsSpotBothStick() {
        // Regression (code review): A's override used to be checked against B's stale auto position.
        val items = copies(SizeMm(35.0, 45.0), 2)
        val auto = layout(items, LayoutStrategy.ROWS)
        val a = auto.placements.first { it.key == "p#0" }.rect
        val b = auto.placements.first { it.key == "p#1" }.rect
        val manual = mapOf(
            "p#1" to ManualPosition(0, b.x, 200.0),
            "p#0" to ManualPosition(0, b.x, b.y),
        )
        val r = layout(items, LayoutStrategy.ROWS, manual = manual)
        assertTrue(r.placements.all { it.manual })
        assertEquals(b.x, r.placements.first { it.key == "p#0" }.rect.x, 1e-9)
        assertTrue(a.x != b.x)
    }

    @Test fun rejectedOverrideNeverLeavesAnOverlap() {
        // A dragged onto B's spot while B stays put: A's override must be dropped.
        val items = copies(SizeMm(35.0, 45.0), 3)
        val auto = layout(items, LayoutStrategy.ROWS)
        val b = auto.placements.first { it.key == "p#1" }.rect
        val r = layout(items, LayoutStrategy.ROWS, manual = mapOf("p#0" to ManualPosition(0, b.x + 3, b.y + 3)))
        assertTrue(r.placements.none { it.manual })
    }

    @Test fun printResolutionIsCappedForHugePhotos() {
        assertEquals(600, io.github.riyadmondol2006.idsheetstudio.image.effectiveDpi(35.0, 45.0, 600))
        val dpi = io.github.riyadmondol2006.idsheetstudio.image.effectiveDpi(400.0, 400.0, 1200)
        val pixels = (400 / 25.4 * dpi) * (400 / 25.4 * dpi)
        assertTrue("dpi=$dpi pixels=$pixels", pixels <= io.github.riyadmondol2006.idsheetstudio.image.MAX_PRINT_PIXELS)
        assertTrue(dpi >= 300)
    }
}
