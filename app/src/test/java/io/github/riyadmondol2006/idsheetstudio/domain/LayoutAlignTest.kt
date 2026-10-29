package io.github.riyadmondol2006.idsheetstudio.domain

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.RectMm
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutItem
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutResult
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.HorizontalAlign
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.ManualPosition
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.VerticalAlign
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Page alignment and "spread evenly" on top of the packers. */
class LayoutAlignTest {
    private val a4 = PaperSizes.A4.size
    private val margins = Margins.all(5.0)
    private val tol = 1e-6

    private fun items(vararg groups: Pair<SizeMm, Int>): List<LayoutItem> {
        var order = 0
        return groups.flatMapIndexed { g, (size, qty) ->
            (0 until qty).map { c -> LayoutItem("p$g#$c", "p$g", order++, c, size) }
        }
    }

    private fun run(
        items: List<LayoutItem>,
        strategy: LayoutStrategy,
        h: HorizontalAlign,
        v: VerticalAlign,
        spread: Boolean,
        spacing: Double = 2.0,
        rotate: Boolean = false,
        page: SizeMm = a4,
        manual: Map<String, ManualPosition> = emptyMap(),
    ): LayoutResult = LayoutEngine.layout(items, page, margins, spacing, strategy, rotate, manual, h, v, spread).also { r ->
        assertEquals("$strategy $h $v spread=$spread", emptyList<String>(), LayoutEngine.validate(r, items))
        assertEquals(items.size, r.placements.size + r.unplaced.size)
        assertSpacing(r, spacing)
    }

    /** Any two automatically placed photos on a page are at least [spacing] apart on some axis. */
    private fun assertSpacing(r: LayoutResult, spacing: Double) {
        r.placements.filter { !it.manual }.groupBy { it.page }.values.forEach { onPage ->
            for (i in onPage.indices) for (j in i + 1 until onPage.size) {
                val a = onPage[i].rect
                val b = onPage[j].rect
                val gap = maxOf(b.x - a.right, a.x - b.right, b.y - a.bottom, a.y - b.bottom)
                assertTrue("gap $gap between ${onPage[i].key} and ${onPage[j].key}", gap >= spacing - tol)
            }
        }
    }

    private fun bounds(rects: List<RectMm>) = RectMm(
        rects.minOf { it.x },
        rects.minOf { it.y },
        rects.maxOf { it.right } - rects.minOf { it.x },
        rects.maxOf { it.bottom } - rects.minOf { it.y },
    )

    // --- Exact offsets ---------------------------------------------------------------------

    @Test fun singlePhotoExactOffsetsForAllAlignments() {
        val one = items(SizeMm(35.0, 45.0) to 1)
        val xs = mapOf(HorizontalAlign.START to 5.0, HorizontalAlign.CENTER to (210.0 - 35.0) / 2, HorizontalAlign.END to 210.0 - 5.0 - 35.0)
        val ys = mapOf(VerticalAlign.TOP to 5.0, VerticalAlign.CENTER to (297.0 - 45.0) / 2, VerticalAlign.BOTTOM to 297.0 - 5.0 - 45.0)
        for (strategy in LayoutStrategy.entries) for (h in HorizontalAlign.entries) for (v in VerticalAlign.entries) for (spread in listOf(false, true)) {
            val p = run(one, strategy, h, v, spread).placements.single()
            assertEquals("$strategy $h $v $spread x", xs.getValue(h), p.rect.x, tol)
            assertEquals("$strategy $h $v $spread y", ys.getValue(v), p.rect.y, tol)
        }
    }

    @Test fun namedSinglePhotoCases() {
        val one = items(SizeMm(35.0, 45.0) to 1)
        run(one, LayoutStrategy.AUTO, HorizontalAlign.CENTER, VerticalAlign.CENTER, false).placements.single().rect.let {
            assertEquals(87.5, it.x, tol)
            assertEquals(126.0, it.y, tol)
        }
        run(one, LayoutStrategy.AUTO, HorizontalAlign.END, VerticalAlign.BOTTOM, false).placements.single().rect.let {
            assertEquals(170.0, it.x, tol)
            assertEquals(247.0, it.y, tol)
        }
        run(one, LayoutStrategy.AUTO, HorizontalAlign.START, VerticalAlign.TOP, false).placements.single().rect.let {
            assertEquals(5.0, it.x, tol)
            assertEquals(5.0, it.y, tol)
        }
    }

    @Test fun defaultsMatchPreviousBehaviour() {
        // Before alignment options existed, content was centred horizontally and top-aligned.
        val list = items(SizeMm(35.0, 45.0) to 7)
        val r = LayoutEngine.layout(list, a4, margins, 2.0, LayoutStrategy.ROWS, false)
        val b = bounds(r.placements.map { it.rect })
        assertEquals(5.0, b.y, tol)
        assertEquals(b.x - 5.0, 205.0 - b.right, tol)
    }

    @Test fun spreadThreeEqualPhotosInOneRowHasEqualGaps() {
        val three = items(SizeMm(35.0, 45.0) to 3)
        for (strategy in listOf(LayoutStrategy.ROWS, LayoutStrategy.AUTO)) {
            val rects = run(three, strategy, HorizontalAlign.CENTER, VerticalAlign.TOP, true).placements.map { it.rect }.sortedBy { it.x }
            val gap = (200.0 - 3 * 35.0) / 2
            assertEquals(5.0, rects[0].x, tol)
            assertEquals(5.0 + 35.0 + gap, rects[1].x, tol)
            assertEquals(205.0 - 35.0, rects[2].x, tol)
            assertEquals(rects[1].x - rects[0].right, rects[2].x - rects[1].right, tol)
            rects.forEach { assertEquals(5.0, it.y, tol) }
        }
        // The single row follows the vertical alignment.
        val bottom = run(three, LayoutStrategy.ROWS, HorizontalAlign.CENTER, VerticalAlign.BOTTOM, true).placements
        bottom.forEach { assertEquals(292.0 - 45.0, it.rect.y, tol) }
    }

    @Test fun spreadRowsFillPageWithEqualGaps() {
        // 5 per row on A4 with 5 mm margins -> rows of 5, 5, 2.
        val list = items(SizeMm(35.0, 45.0) to 12)
        val r = run(list, LayoutStrategy.ROWS, HorizontalAlign.START, VerticalAlign.TOP, true)
        val rows = r.placements.groupBy { it.rect.y }.toSortedMap()
        assertEquals(listOf(5, 5, 2), rows.values.map { it.size })
        val tops = rows.keys.toList()
        val rowGap = (287.0 - 3 * 45.0) / 2
        assertEquals(5.0, tops[0], tol)
        assertEquals(5.0 + 45.0 + rowGap, tops[1], tol)
        assertEquals(292.0 - 45.0, tops[2], tol)
        for (row in rows.values) {
            val sorted = row.map { it.rect }.sortedBy { it.x }
            assertEquals(5.0, sorted.first().x, tol)
            assertEquals(205.0, sorted.last().right, tol)
            val gaps = sorted.zipWithNext { a, b -> b.x - a.right }
            gaps.forEach { assertEquals(gaps.first(), it, tol) }
            assertTrue(gaps.first() >= 2.0)
        }
    }

    @Test fun spreadRowWithSinglePhotoFollowsHorizontalAlignment() {
        // 6 copies -> a row of 5 and a lone photo on the second row.
        val list = items(SizeMm(35.0, 45.0) to 6)
        val expected = mapOf(HorizontalAlign.START to 5.0, HorizontalAlign.CENTER to 87.5, HorizontalAlign.END to 170.0)
        for (h in HorizontalAlign.entries) {
            val r = run(list, LayoutStrategy.ROWS, h, VerticalAlign.TOP, true)
            val lone = r.placements.groupBy { it.rect.y }.values.single { it.size == 1 }.single()
            assertEquals(h.name, expected.getValue(h), lone.rect.x, tol)
            assertEquals(292.0 - 45.0, lone.rect.y, tol)
        }
    }

    @Test fun spreadDenseGridReachesAllEdgesWithEqualGaps() {
        val list = items(SizeMm(35.0, 45.0) to 30)
        val r = run(list, LayoutStrategy.DENSE, HorizontalAlign.CENTER, VerticalAlign.CENTER, true)
        val b = bounds(r.placements.map { it.rect })
        assertEquals(r.printableArea.x, b.x, tol)
        assertEquals(r.printableArea.y, b.y, tol)
        assertEquals(r.printableArea.right, b.right, tol)
        assertEquals(r.printableArea.bottom, b.bottom, tol)
        val xs = r.placements.map { it.rect.x }.distinctBy { Math.round(it * 1e4) }.sorted()
        val colGaps = xs.zipWithNext { a, c -> c - a - 35.0 }
        colGaps.forEach { assertEquals(colGaps.first(), it, 1e-4) }
    }

    @Test fun spreadNeverChangesPageCountOrUnplaced() {
        val list = items(SizeMm(35.0, 45.0) to 31, SizeMm(250.0, 50.0) to 1)
        for (strategy in LayoutStrategy.entries) {
            val plain = run(list, strategy, HorizontalAlign.CENTER, VerticalAlign.TOP, false)
            val spread = run(list, strategy, HorizontalAlign.CENTER, VerticalAlign.TOP, true)
            assertEquals(plain.pageCount, spread.pageCount)
            assertEquals(plain.unplaced, spread.unplaced)
        }
    }

    // --- Every combination ----------------------------------------------------------------

    @Test fun everyAlignmentStrategyAndSpreadCombinationIsValid() {
        val random = Random(2024)
        repeat(30) { case ->
            val groups = (1..random.nextInt(1, 5)).map {
                SizeMm(random.nextInt(15, 90).toDouble(), random.nextInt(15, 110).toDouble()) to random.nextInt(1, 20)
            }.toTypedArray()
            val list = items(*groups)
            val spacing = random.nextInt(0, 6).toDouble()
            for (rotate in listOf(false, true)) for (strategy in LayoutStrategy.entries) {
                val reference = run(list, strategy, HorizontalAlign.START, VerticalAlign.TOP, false, spacing, rotate)
                for (h in HorizontalAlign.entries) for (v in VerticalAlign.entries) for (spread in listOf(false, true)) {
                    val label = "case $case $strategy rotate=$rotate $h $v spread=$spread"
                    val r = run(list, strategy, h, v, spread, spacing, rotate)
                    assertEquals(label, reference.pageCount, r.pageCount)
                    assertEquals(label, reference.placements.map { it.key }.toSet(), r.placements.map { it.key }.toSet())
                    for (page in 0 until r.pageCount) {
                        val onPage = r.placementsOn(page)
                        if (onPage.isEmpty()) continue
                        val b = bounds(onPage.map { it.rect })
                        val area = r.printableArea
                        if (!spread) {
                            assertAligned(label, b, area, h, v)
                            // Plain alignment is a pure translation of the packed block.
                            val ref = reference.placementsOn(page).associateBy { it.key }
                            val refB = bounds(ref.values.map { it.rect })
                            onPage.forEach { p ->
                                val q = ref.getValue(p.key).rect
                                assertEquals(label, q.x - refB.x, p.rect.x - b.x, tol)
                                assertEquals(label, q.y - refB.y, p.rect.y - b.y, tol)
                                assertEquals(label, q.width, p.rect.width, 0.0)
                            }
                        } else if (onPage.size > 1) {
                            // Spreading only ever pushes content outwards to the area edges.
                            assertTrue(label, b.width >= bounds(reference.placementsOn(page).map { it.rect }).width - tol)
                            assertTrue(label, b.height >= bounds(reference.placementsOn(page).map { it.rect }).height - tol)
                        }
                    }
                }
            }
        }
    }

    private fun assertAligned(label: String, b: RectMm, area: RectMm, h: HorizontalAlign, v: VerticalAlign) {
        when (h) {
            HorizontalAlign.START -> assertEquals(label, area.x, b.x, tol)
            HorizontalAlign.CENTER -> assertEquals(label, b.x - area.x, area.right - b.right, tol)
            HorizontalAlign.END -> assertEquals(label, area.right, b.right, tol)
        }
        when (v) {
            VerticalAlign.TOP -> assertEquals(label, area.y, b.y, tol)
            VerticalAlign.CENTER -> assertEquals(label, b.y - area.y, area.bottom - b.bottom, tol)
            VerticalAlign.BOTTOM -> assertEquals(label, area.bottom, b.bottom, tol)
        }
    }

    // --- Manual positions and settings plumbing -------------------------------------------

    @Test fun manualPositionsStayAbsoluteWithAlignment() {
        val list = items(SizeMm(35.0, 45.0) to 2)
        val spots = listOf(20.0 to 30.0, 120.0 to 200.0, 20.0 to 200.0, 120.0 to 30.0)
        for (strategy in LayoutStrategy.entries) for (h in HorizontalAlign.entries) for (v in VerticalAlign.entries) for (spread in listOf(false, true)) {
            // Pick a spot clear of the other copy's aligned automatic position.
            val other = run(list, strategy, h, v, spread).placements.first { it.key == "p0#0" }.rect
            val (x, y) = spots.first { (x, y) -> !RectMm(x, y, 35.0, 45.0).intersects(other) }
            val r = run(list, strategy, h, v, spread, manual = mapOf("p0#1" to ManualPosition(0, x, y)))
            val moved = r.placements.first { it.key == "p0#1" }
            assertTrue("$strategy $h $v $spread", moved.manual)
            assertEquals(x, moved.rect.x, 0.0)
            assertEquals(y, moved.rect.y, 0.0)
            assertEquals(other, r.placements.first { it.key == "p0#0" }.rect)
        }
        // A drag that collides with the aligned automatic position is still rejected.
        val bottomRight = run(list, LayoutStrategy.ROWS, HorizontalAlign.END, VerticalAlign.BOTTOM, false)
        val first = bottomRight.placements.first { it.key == "p0#0" }.rect
        val clash = mapOf("p0#1" to ManualPosition(0, first.x - 10, first.y - 10))
        val r = run(list, LayoutStrategy.ROWS, HorizontalAlign.END, VerticalAlign.BOTTOM, false, manual = clash)
        assertFalse(r.placements.first { it.key == "p0#1" }.manual)
    }

    @Test fun settingsArePassedThroughToLayout() {
        val photo = PhotoItem(
            id = "a", projectId = "proj", orderIndex = 0, originalPath = "", cutoutPath = null,
            sourceWidth = 100, sourceHeight = 100, presetId = "custom", size = SizeMm(35.0, 45.0), quantity = 1,
            transform = PhotoTransform(), useCutout = false, backgroundColor = 0xFFFFFFFF,
            backgroundState = BackgroundState.ORIGINAL, backgroundError = null, label = "", revision = 0,
        )
        val settings = SheetSettings(horizontalAlign = HorizontalAlign.END, verticalAlign = VerticalAlign.BOTTOM)
        val rect = LayoutEngine.layout(listOf(photo), settings).placements.single().rect
        assertEquals(170.0, rect.x, tol)
        assertEquals(247.0, rect.y, tol)

        val three = listOf(photo.copy(quantity = 3))
        val spread = LayoutEngine.layout(three, SheetSettings(strategy = LayoutStrategy.ROWS, spreadEvenly = true))
        assertEquals(205.0, spread.placements.maxOf { it.rect.right }, tol)
        assertEquals(5.0, spread.placements.minOf { it.rect.x }, tol)
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test fun oldSavedSettingsDecodeWithAlignmentDefaults() {
        val old = """
            {"paperId":"a4","customPaper":{"width":210.0,"height":297.0},"orientation":"LANDSCAPE",
             "margins":{"top":7.0,"right":7.0,"bottom":7.0,"left":7.0},"spacingMm":3.0,
             "strategy":"ROWS","allowRotation":true,"displayUnit":"MM","manualPositions":{},
             "someRemovedField":42}
        """.trimIndent()
        val s = json.decodeFromString(SheetSettings.serializer(), old)
        assertEquals(HorizontalAlign.CENTER, s.horizontalAlign)
        assertEquals(VerticalAlign.TOP, s.verticalAlign)
        assertFalse(s.spreadEvenly)
        assertEquals(3.0, s.spacingMm, 0.0)
        assertEquals(LayoutStrategy.ROWS, s.strategy)
        assertTrue(s.allowRotation)

        val empty = json.decodeFromString(SheetSettings.serializer(), "{}")
        assertEquals(SheetSettings(), empty)
    }

    @Test fun alignmentSettingsRoundTrip() {
        val s = SheetSettings(horizontalAlign = HorizontalAlign.END, verticalAlign = VerticalAlign.CENTER, spreadEvenly = true)
        val text = json.encodeToString(SheetSettings.serializer(), s)
        assertTrue(text.contains("\"horizontalAlign\":\"END\""))
        assertEquals(s, json.decodeFromString(SheetSettings.serializer(), text))
    }
}
