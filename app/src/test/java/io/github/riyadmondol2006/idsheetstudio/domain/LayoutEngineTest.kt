package io.github.riyadmondol2006.idsheetstudio.domain

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.ManualPosition
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class LayoutEngineTest {
    private val a4 = PaperSizes.A4.size

    private fun items(vararg groups: Pair<SizeMm, Int>): List<LayoutItem> {
        var order = 0
        return groups.flatMapIndexed { g, (size, qty) ->
            (0 until qty).map { c -> LayoutItem("p$g#$c", "p$g", order++, c, size) }
        }
    }

    private fun run(items: List<LayoutItem>, strategy: LayoutStrategy, spacing: Double = 2.0, margins: Margins = Margins.all(5.0), page: SizeMm = a4, rotate: Boolean = false) =
        LayoutEngine.layout(items, page, margins, spacing, strategy, rotate).also { result ->
            assertEquals(emptyList<String>(), LayoutEngine.validate(result, items))
        }

    @Test fun passportGridOnA4() {
        // Printable 200 x 287 mm; 35+2 spacing -> 5 columns ((200+2)/37), 45+2 -> 6 rows ((287+2)/47).
        val list = items(SizeMm(35.0, 45.0) to 30)
        for (strategy in LayoutStrategy.entries) {
            val r = run(list, strategy)
            assertEquals(1, r.pageCount)
            assertEquals(30, r.placements.size)
        }
    }

    @Test fun overflowCreatesPagesWithoutLosingCopies() {
        val list = items(SizeMm(35.0, 45.0) to 31)
        val r = run(list, LayoutStrategy.ROWS)
        assertEquals(2, r.pageCount)
        assertEquals(31, r.placements.size)
        assertEquals(1, r.placementsOn(1).size)
    }

    @Test fun spacingIsRespectedBetweenNeighbours() {
        val spacing = 3.0
        val r = run(items(SizeMm(35.0, 45.0) to 12, SizeMm(20.0, 25.0) to 10), LayoutStrategy.AUTO, spacing = spacing)
        r.placements.groupBy { it.page }.values.forEach { onPage ->
            for (i in onPage.indices) for (j in i + 1 until onPage.size) {
                val a = onPage[i].rect
                val b = onPage[j].rect
                val gapX = maxOf(b.x - a.right, a.x - b.right)
                val gapY = maxOf(b.y - a.bottom, a.y - b.bottom)
                assertTrue("gap between ${onPage[i].key} and ${onPage[j].key}", maxOf(gapX, gapY) >= spacing - 1e-6)
            }
        }
    }

    @Test fun marginsAreExact() {
        val margins = Margins(top = 12.0, right = 7.0, bottom = 9.5, left = 15.0)
        val r = run(items(SizeMm(35.0, 45.0) to 40), LayoutStrategy.DENSE, margins = margins)
        assertEquals(15.0, r.printableArea.x, 0.0)
        assertEquals(12.0, r.printableArea.y, 0.0)
        assertEquals(210.0 - 22.0, r.printableArea.width, 1e-9)
        assertEquals(297.0 - 21.5, r.printableArea.height, 1e-9)
        r.placements.forEach {
            assertTrue(it.rect.x >= 15.0 - 1e-9 && it.rect.right <= 203.0 + 1e-9)
            assertTrue(it.rect.y >= 12.0 - 1e-9 && it.rect.bottom <= 287.5 + 1e-9)
        }
    }

    @Test fun mixedSizesNeverOverlapOrStretch() {
        val random = Random(42)
        repeat(60) {
            val groups = (1..random.nextInt(1, 6)).map {
                SizeMm(random.nextInt(15, 90).toDouble(), random.nextInt(15, 110).toDouble()) to random.nextInt(1, 25)
            }.toTypedArray()
            val list = items(*groups)
            for (strategy in LayoutStrategy.entries) {
                val r = run(list, strategy, spacing = random.nextInt(0, 6).toDouble(), rotate = random.nextBoolean())
                assertEquals(list.size, r.placements.size + r.unplaced.size)
                assertTrue(r.unplaced.isEmpty())
            }
        }
    }

    @Test fun autoIsNeverWorseThanEitherStrategy() {
        val random = Random(7)
        repeat(40) {
            val list = items(
                SizeMm(random.nextInt(20, 60).toDouble(), random.nextInt(20, 70).toDouble()) to random.nextInt(1, 40),
                SizeMm(random.nextInt(15, 40).toDouble(), random.nextInt(15, 50).toDouble()) to random.nextInt(1, 40),
            )
            val auto = run(list, LayoutStrategy.AUTO)
            val rows = run(list, LayoutStrategy.ROWS)
            val dense = run(list, LayoutStrategy.DENSE)
            assertTrue(auto.pageCount <= minOf(rows.pageCount, dense.pageCount))
        }
    }

    @Test fun oversizedPhotoIsReportedNotDropped() {
        val list = items(SizeMm(250.0, 50.0) to 2, SizeMm(35.0, 45.0) to 3)
        val r = run(list, LayoutStrategy.AUTO)
        assertEquals(2, r.unplaced.size)
        assertEquals(3, r.placements.size)
        // With rotation the 250 x 50 strip fits on A4 portrait (50 x 250).
        val rotated = run(list, LayoutStrategy.AUTO, rotate = true)
        assertEquals(0, rotated.unplaced.size)
        assertTrue(rotated.placements.filter { it.photoId == "p0" }.all { it.rotated && it.rect.width == 50.0 && it.rect.height == 250.0 })
    }

    @Test fun impossibleMarginsYieldNoPages() {
        val list = items(SizeMm(35.0, 45.0) to 2)
        val r = LayoutEngine.layout(list, a4, Margins.all(150.0), 2.0, LayoutStrategy.AUTO, false)
        assertFalse(r.isValidPrintableArea)
        assertEquals(0, r.pageCount)
        assertEquals(2, r.unplaced.size)
    }

    @Test fun manualPositionIsAppliedWhenFree() {
        val list = items(SizeMm(35.0, 45.0) to 2)
        val manual = mapOf("p0#1" to ManualPosition(0, 120.0, 200.0))
        val r = LayoutEngine.layout(list, a4, Margins.all(5.0), 2.0, LayoutStrategy.ROWS, false, manual)
        val moved = r.placements.first { it.key == "p0#1" }
        assertTrue(moved.manual)
        assertEquals(120.0, moved.rect.x, 0.0)
        assertEquals(200.0, moved.rect.y, 0.0)
        assertEquals(emptyList<String>(), LayoutEngine.validate(r, list))
    }

    @Test fun manualPositionRejectedWhenOverlappingOrOutside() {
        val list = items(SizeMm(35.0, 45.0) to 2)
        val first = LayoutEngine.layout(list, a4, Margins.all(5.0), 2.0, LayoutStrategy.ROWS, false).placements.first { it.key == "p0#0" }
        val overlap = mapOf("p0#1" to ManualPosition(0, first.rect.x + 10, first.rect.y + 10))
        val outside = mapOf("p0#1" to ManualPosition(0, 190.0, 10.0))
        for (manual in listOf(overlap, outside)) {
            val r = LayoutEngine.layout(list, a4, Margins.all(5.0), 2.0, LayoutStrategy.ROWS, false, manual)
            assertFalse(r.placements.first { it.key == "p0#1" }.manual)
            assertEquals(emptyList<String>(), LayoutEngine.validate(r, list))
        }
    }

    @Test fun landscapeLetterFitsMore() {
        val list = items(SizeMm(50.8, 50.8) to 20)
        val portrait = run(list, LayoutStrategy.ROWS, page = PaperSizes.LETTER.size)
        val landscape = run(list, LayoutStrategy.ROWS, page = PaperSizes.LETTER.size.rotated())
        assertEquals(20, portrait.placements.size)
        assertEquals(20, landscape.placements.size)
        assertTrue(portrait.pageCount >= 1 && landscape.pageCount >= 1)
    }

    @Test fun zeroQuantityProducesNoPlacements() {
        val r = LayoutEngine.layout(emptyList(), a4, Margins.all(5.0), 2.0, LayoutStrategy.AUTO, false)
        assertEquals(0, r.pageCount)
        assertTrue(r.placements.isEmpty())
    }
}
