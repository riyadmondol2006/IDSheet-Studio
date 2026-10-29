package io.github.riyadmondol2006.idsheetstudio.domain.layout

import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.RectMm
import io.github.riyadmondol2006.idsheetstudio.core.RectMm.Companion.EPSILON
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.model.HorizontalAlign
import io.github.riyadmondol2006.idsheetstudio.domain.model.LayoutStrategy
import io.github.riyadmondol2006.idsheetstudio.domain.model.ManualPosition
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.VerticalAlign
import kotlin.math.max
import kotlin.math.min

/** One physical copy of a photo that must be placed on a page. */
data class LayoutItem(
    val key: String,
    val photoId: String,
    val order: Int,
    val copy: Int,
    val size: SizeMm,
)

/**
 * A placed copy. [rect] is in page coordinates (millimetres, top-left origin) and already reflects
 * [rotated] (a rotated 35 × 45 photo occupies a 45 × 35 rectangle).
 */
data class Placement(
    val key: String,
    val photoId: String,
    val page: Int,
    val rect: RectMm,
    val rotated: Boolean,
    val manual: Boolean = false,
)

data class LayoutResult(
    val pageSize: SizeMm,
    val printableArea: RectMm,
    val pageCount: Int,
    val placements: List<Placement>,
    val unplaced: List<LayoutItem>,
    val strategy: LayoutStrategy,
    val spacingMm: Double,
) {
    fun placementsOn(page: Int): List<Placement> = placements.filter { it.page == page }

    /** Fraction of the printable area covered by photos, over all pages. */
    val utilization: Double
        get() {
            if (pageCount == 0) return 0.0
            val used = placements.sumOf { it.rect.width * it.rect.height }
            return used / (printableArea.width * printableArea.height * pageCount)
        }

    val isValidPrintableArea: Boolean get() = printableArea.width > 0 && printableArea.height > 0
}

object LayoutEngine {

    /** Expands photos into individual copies, in photo order. */
    fun expand(photos: List<PhotoItem>): List<LayoutItem> =
        photos.sortedBy { it.orderIndex }.flatMap { photo ->
            (0 until photo.quantity.coerceAtLeast(0)).map { copy ->
                LayoutItem(placementKey(photo.id, copy), photo.id, photo.orderIndex, copy, photo.size)
            }
        }

    fun placementKey(photoId: String, copy: Int) = "$photoId#$copy"

    fun layout(photos: List<PhotoItem>, settings: SheetSettings): LayoutResult = layout(
        items = expand(photos),
        pageSize = settings.pageSize,
        margins = settings.margins,
        spacing = settings.spacingMm,
        strategy = settings.strategy,
        allowRotation = settings.allowRotation,
        manualPositions = settings.manualPositions,
        horizontalAlign = settings.horizontalAlign,
        verticalAlign = settings.verticalAlign,
        spreadEvenly = settings.spreadEvenly,
    )

    fun printableArea(pageSize: SizeMm, margins: Margins): RectMm = RectMm(
        x = margins.left,
        y = margins.top,
        width = pageSize.width - margins.left - margins.right,
        height = pageSize.height - margins.top - margins.bottom,
    )

    /**
     * Packs [items] onto as few pages as possible, then positions each page's content inside the
     * printable area:
     *
     * - Without [spreadEvenly] the packed block keeps its spacing and is moved as a whole to
     *   [horizontalAlign] / [verticalAlign].
     * - With [spreadEvenly] and a neat-rows result, rows are distributed from the top to the
     *   bottom of the printable area and the photos of each row from its left to its right edge,
     *   with equal gaps that are never smaller than [spacing]. A page with a single row uses
     *   [verticalAlign]; a row with a single photo uses [horizontalAlign].
     * - With [spreadEvenly] and a max-fit (dense) result there are no well-defined rows, so the
     *   photo positions (never their sizes) are stretched away from the top-left corner until the
     *   arrangement touches the right and bottom edges of the printable area. Stretching only
     *   widens the gaps, so photos never overlap and stay at least [spacing] apart; for a regular
     *   grid the gaps come out equal. An axis with nothing to stretch (a single column or row)
     *   falls back to the alignment.
     *
     * Manual positions are absolute page coordinates and are applied afterwards, unchanged.
     */
    fun layout(
        items: List<LayoutItem>,
        pageSize: SizeMm,
        margins: Margins,
        spacing: Double,
        strategy: LayoutStrategy,
        allowRotation: Boolean,
        manualPositions: Map<String, ManualPosition> = emptyMap(),
        horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER,
        verticalAlign: VerticalAlign = VerticalAlign.TOP,
        spreadEvenly: Boolean = false,
    ): LayoutResult {
        val area = printableArea(pageSize, margins)
        val gap = spacing.coerceAtLeast(0.0)
        if (area.width <= EPSILON || area.height <= EPSILON || items.isEmpty()) {
            return LayoutResult(pageSize, area, if (items.isEmpty()) 0 else 0, emptyList(), items, strategy, gap)
        }

        val packed: Packed = when (strategy) {
            LayoutStrategy.ROWS -> ShelfPacker.pack(items, area.width, area.height, gap, allowRotation)
            LayoutStrategy.DENSE -> densest(items, area, gap, allowRotation)
            LayoutStrategy.AUTO -> {
                val rows = ShelfPacker.pack(items, area.width, area.height, gap, allowRotation)
                val dense = densest(items, area, gap, allowRotation)
                if (dense.betterThan(rows)) dense else rows
            }
        }

        // Arrange each page in printable-area coordinates, then translate to page coordinates.
        var placements = packed.placements.groupBy { it.page }.flatMap { (_, onPage) ->
            val spread = when {
                !spreadEvenly -> onPage
                packed.rows -> spreadRows(onPage, area.width, area.height, gap, horizontalAlign)
                else -> stretch(onPage, area.width, area.height)
            }
            align(spread, area.width, area.height, horizontalAlign, verticalAlign)
                .map { it.copy(rect = it.rect.offset(area.x, area.y)) }
        }.sortedWith(compareBy({ it.page }, { it.rect.y }, { it.rect.x }))

        placements = applyManualPositions(placements, manualPositions, area)

        return LayoutResult(
            pageSize = pageSize,
            printableArea = area,
            pageCount = packed.pageCount,
            placements = placements,
            unplaced = packed.unplaced,
            strategy = strategy,
            spacingMm = gap,
        )
    }

    /** Where a block of [size] starts inside [available] for the given alignment. */
    private fun alignedStart(available: Double, size: Double, h: HorizontalAlign): Double = when (h) {
        HorizontalAlign.START -> 0.0
        HorizontalAlign.CENTER -> ((available - size) / 2).coerceAtLeast(0.0)
        HorizontalAlign.END -> (available - size).coerceAtLeast(0.0)
    }

    private fun alignedStart(available: Double, size: Double, v: VerticalAlign): Double = when (v) {
        VerticalAlign.TOP -> 0.0
        VerticalAlign.CENTER -> ((available - size) / 2).coerceAtLeast(0.0)
        VerticalAlign.BOTTOM -> (available - size).coerceAtLeast(0.0)
    }

    /** Moves the bounding box of one page's placements (area coordinates) to the alignment. */
    private fun align(
        onPage: List<Placement>,
        areaW: Double,
        areaH: Double,
        h: HorizontalAlign,
        v: VerticalAlign,
    ): List<Placement> {
        val minX = onPage.minOf { it.rect.x }
        val minY = onPage.minOf { it.rect.y }
        val maxRight = onPage.maxOf { it.rect.right }
        val maxBottom = onPage.maxOf { it.rect.bottom }
        val dx = alignedStart(areaW, maxRight - minX, h) - minX
        val dy = alignedStart(areaH, maxBottom - minY, v) - minY
        return onPage.map { it.copy(rect = it.rect.offset(dx, dy)) }
    }

    /**
     * Spreads a neat-rows page: rows (placements sharing a shelf top) are distributed vertically
     * and photos within each row horizontally, with equal gaps of at least [gap]. Photos keep their
     * position relative to the top of their row.
     */
    private fun spreadRows(
        onPage: List<Placement>,
        areaW: Double,
        areaH: Double,
        gap: Double,
        h: HorizontalAlign,
    ): List<Placement> {
        val rows = onPage.groupBy { it.rect.y }.toSortedMap().values.map { row -> row.sortedBy { it.rect.x } }
        val heights = rows.map { row -> row.maxOf { it.rect.height } }
        val rowGap = if (rows.size > 1) (areaH - heights.sum()) / (rows.size - 1) else 0.0
        val spreadVertically = rows.size > 1 && rowGap >= gap - EPSILON
        var top = 0.0
        return rows.flatMapIndexed { index, row ->
            val rowY = if (spreadVertically) top else row.first().rect.y
            top += heights[index] + rowGap
            val shelfY = row.first().rect.y
            val widths = row.sumOf { it.rect.width }
            val colGap = if (row.size > 1) (areaW - widths) / (row.size - 1) else 0.0
            var x = when {
                row.size == 1 -> alignedStart(areaW, row.first().rect.width, h)
                colGap >= gap - EPSILON -> 0.0
                else -> row.first().rect.x
            }
            row.map { p ->
                val newX = if (row.size > 1 && colGap < gap - EPSILON) p.rect.x else x
                x = newX + p.rect.width + colGap
                p.copy(rect = p.rect.copy(x = newX, y = rowY + (p.rect.y - shelfY)))
            }
        }
    }

    /**
     * Spreads an arbitrary (dense) page by scaling every photo's top-left corner away from the
     * area origin, independently per axis, by the largest factor that keeps every photo inside
     * the area. Factors are at least 1, so any gap between two photos can only grow.
     */
    private fun stretch(onPage: List<Placement>, areaW: Double, areaH: Double): List<Placement> {
        val sx = onPage.filter { it.rect.x > EPSILON }.minOfOrNull { (areaW - it.rect.width) / it.rect.x }?.coerceAtLeast(1.0) ?: 1.0
        val sy = onPage.filter { it.rect.y > EPSILON }.minOfOrNull { (areaH - it.rect.height) / it.rect.y }?.coerceAtLeast(1.0) ?: 1.0
        return onPage.map { p ->
            val x = (p.rect.x * sx).coerceIn(0.0, (areaW - p.rect.width).coerceAtLeast(0.0))
            val y = (p.rect.y * sy).coerceIn(0.0, (areaH - p.rect.height).coerceAtLeast(0.0))
            p.copy(rect = p.rect.copy(x = x, y = y))
        }
    }

    private fun densest(items: List<LayoutItem>, area: RectMm, gap: Double, allowRotation: Boolean): Packed {
        val candidates = MaxRectsPacker.Heuristic.entries.map {
            MaxRectsPacker.pack(items, area.width, area.height, gap, allowRotation, it)
        }
        return candidates.reduce { best, next -> if (next.betterThan(best)) next else best }
    }

    /**
     * Applies user-dragged positions. Every candidate override must stay inside the printable
     * area; then, while any two photos overlap, the override involved in the clash is dropped
     * (later photos first) and that photo returns to its automatic position. Repeating until no
     * overlap remains makes the result independent of drag order (e.g. moving B away and then A
     * into B's old spot both stick).
     */
    fun applyManualPositions(
        placements: List<Placement>,
        manual: Map<String, ManualPosition>,
        area: RectMm,
    ): List<Placement> {
        if (manual.isEmpty()) return placements
        val accepted = LinkedHashMap<Int, RectMm>()
        placements.forEachIndexed { index, p ->
            val override = manual[p.key] ?: return@forEachIndexed
            if (override.page != p.page) return@forEachIndexed
            val candidate = p.rect.copy(x = override.x, y = override.y)
            if (area.containsRect(candidate)) accepted[index] = candidate
        }
        while (true) {
            val rects = placements.mapIndexed { i, p -> accepted[i] ?: p.rect }
            var drop: Int? = null
            loop@ for (i in placements.indices.reversed()) for (j in placements.indices) {
                if (i == j || placements[i].page != placements[j].page) continue
                if ((i in accepted || j in accepted) && rects[i].intersects(rects[j])) {
                    drop = if (i in accepted) i else j
                    break@loop
                }
            }
            if (drop == null) break
            accepted.remove(drop)
        }
        return placements.mapIndexed { i, p -> accepted[i]?.let { p.copy(rect = it, manual = true) } ?: p }
    }

    /** True when [rect] lies inside [area] and overlaps none of [others]. */
    fun isFree(rect: RectMm, others: List<Placement>, area: RectMm): Boolean =
        area.containsRect(rect) && others.none { it.rect.intersects(rect) }

    /**
     * Validates a layout: every copy placed exactly once (or reported as unplaced), all inside
     * the printable area, no overlaps, no scaling. Returns a list of human readable problems.
     */
    fun validate(result: LayoutResult, expected: List<LayoutItem>): List<String> {
        val problems = mutableListOf<String>()
        val byKey = expected.associateBy { it.key }
        val seen = HashSet<String>()
        for (p in result.placements) {
            val item = byKey[p.key]
            if (item == null) {
                problems += "Unknown placement ${p.key}"
                continue
            }
            if (!seen.add(p.key)) problems += "Duplicate placement ${p.key}"
            val expectedSize = if (p.rotated) item.size.rotated() else item.size
            if (kotlin.math.abs(p.rect.width - expectedSize.width) > EPSILON ||
                kotlin.math.abs(p.rect.height - expectedSize.height) > EPSILON
            ) {
                problems += "Size changed for ${p.key}: ${p.rect.width}x${p.rect.height}"
            }
            if (!result.printableArea.containsRect(p.rect)) problems += "${p.key} outside printable area"
            if (p.page !in 0 until result.pageCount) problems += "${p.key} on invalid page ${p.page}"
        }
        val unplacedKeys = result.unplaced.map { it.key }.toSet()
        for (item in expected) {
            if (item.key !in seen && item.key !in unplacedKeys) problems += "Missing copy ${item.key}"
        }
        result.placements.groupBy { it.page }.forEach { (_, onPage) ->
            for (i in onPage.indices) for (j in i + 1 until onPage.size) {
                if (onPage[i].rect.intersects(onPage[j].rect)) {
                    problems += "Overlap ${onPage[i].key} / ${onPage[j].key}"
                }
            }
        }
        return problems
    }
}

/** Packing output in printable-area coordinates. */
internal data class Packed(
    val placements: List<Placement>,
    val pageCount: Int,
    val unplaced: List<LayoutItem>,
    /** True for shelf packing, where placements sharing a `y` form one straight row. */
    val rows: Boolean = false,
) {
    private val lastPageFill: Double
        get() = placements.filter { it.page == pageCount - 1 }.sumOf { it.rect.width * it.rect.height }

    /** Fewer missing copies, then fewer pages, then emptier last page (more compact). */
    fun betterThan(other: Packed): Boolean = when {
        unplaced.size != other.unplaced.size -> unplaced.size < other.unplaced.size
        pageCount != other.pageCount -> pageCount < other.pageCount
        else -> lastPageFill < other.lastPageFill - EPSILON
    }
}

private fun fits(w: Double, h: Double, areaW: Double, areaH: Double) =
    w <= areaW + EPSILON && h <= areaH + EPSILON

/**
 * "Neat rows" packing (first-fit decreasing height shelves). Produces straight rows that are easy
 * to cut with a guillotine trimmer. Every open page and shelf is reconsidered for each copy, so
 * small photos fill gaps left on earlier pages.
 */
internal object ShelfPacker {
    private class Shelf(val y: Double, val height: Double) {
        var cursor = 0.0
    }

    private class Page(val index: Int) {
        val shelves = mutableListOf<Shelf>()
        var nextY = 0.0
    }

    fun pack(items: List<LayoutItem>, areaW: Double, areaH: Double, gap: Double, allowRotation: Boolean): Packed {
        val sorted = items.sortedWith(
            compareByDescending<LayoutItem> { it.size.height }
                .thenByDescending { it.size.width }
                .thenBy { it.order }
                .thenBy { it.copy },
        )
        val pages = mutableListOf<Page>()
        val placements = mutableListOf<Placement>()
        val unplaced = mutableListOf<LayoutItem>()

        for (item in sorted) {
            val orientations = buildList {
                if (fits(item.size.width, item.size.height, areaW, areaH)) add(false)
                if (allowRotation && fits(item.size.height, item.size.width, areaW, areaH)) add(true)
            }
            if (orientations.isEmpty()) {
                unplaced += item
                continue
            }
            var placed = false
            // 1) An existing shelf with room.
            loop@ for (page in pages) for (shelf in page.shelves) for (rotated in orientations) {
                val w = if (rotated) item.size.height else item.size.width
                val h = if (rotated) item.size.width else item.size.height
                if (h <= shelf.height + EPSILON && shelf.cursor + w <= areaW + EPSILON) {
                    placements += Placement(item.key, item.photoId, page.index, RectMm(shelf.cursor, shelf.y, w, h), rotated)
                    shelf.cursor += w + gap
                    placed = true
                    break@loop
                }
            }
            // 2) A new shelf on an existing page, then 3) a new page.
            if (!placed) {
                val target = pages.firstOrNull { page -> orientations.any { r -> page.nextY + (if (r) item.size.width else item.size.height) <= areaH + EPSILON } }
                    ?: Page(pages.size).also { pages += it }
                val rotated = orientations.first { r -> target.nextY + (if (r) item.size.width else item.size.height) <= areaH + EPSILON }
                val w = if (rotated) item.size.height else item.size.width
                val h = if (rotated) item.size.width else item.size.height
                val shelf = Shelf(target.nextY, h)
                target.shelves += shelf
                target.nextY += h + gap
                placements += Placement(item.key, item.photoId, target.index, RectMm(0.0, shelf.y, w, h), rotated)
                shelf.cursor = w + gap
            }
        }
        return Packed(placements, pages.size, unplaced, rows = true)
    }
}

/**
 * Maximal-rectangles packing for the densest arrangement of mixed sizes. Spacing is modelled by
 * growing every item and the bin by the gap, which guarantees the gap between neighbours while
 * still allowing photos to touch the printable-area edge.
 */
internal object MaxRectsPacker {
    enum class Heuristic { BEST_SHORT_SIDE, BOTTOM_LEFT, BEST_AREA }

    private class Bin(val index: Int, w: Double, h: Double) {
        val free = mutableListOf(RectMm(0.0, 0.0, w, h))

        fun find(w: Double, h: Double, heuristic: Heuristic): Candidate? {
            var best: Candidate? = null
            for (fr in free) {
                if (w > fr.width + EPSILON || h > fr.height + EPSILON) continue
                val leftoverW = fr.width - w
                val leftoverH = fr.height - h
                val (s1, s2) = when (heuristic) {
                    Heuristic.BEST_SHORT_SIDE -> min(leftoverW, leftoverH) to max(leftoverW, leftoverH)
                    Heuristic.BOTTOM_LEFT -> fr.y + h to fr.x
                    Heuristic.BEST_AREA -> (fr.width * fr.height - w * h) to min(leftoverW, leftoverH)
                }
                val c = Candidate(this, RectMm(fr.x, fr.y, w, h), s1, s2)
                if (best == null || c.better(best)) best = c
            }
            return best
        }

        fun place(rect: RectMm) {
            val next = mutableListOf<RectMm>()
            for (fr in free) {
                if (!fr.intersects(rect)) {
                    next += fr
                    continue
                }
                if (rect.x > fr.x + EPSILON) next += RectMm(fr.x, fr.y, rect.x - fr.x, fr.height)
                if (rect.right < fr.right - EPSILON) next += RectMm(rect.right, fr.y, fr.right - rect.right, fr.height)
                if (rect.y > fr.y + EPSILON) next += RectMm(fr.x, fr.y, fr.width, rect.y - fr.y)
                if (rect.bottom < fr.bottom - EPSILON) next += RectMm(fr.x, rect.bottom, fr.width, fr.bottom - rect.bottom)
            }
            // Remove free rectangles fully contained in another one.
            free.clear()
            for ((i, a) in next.withIndex()) {
                val contained = next.withIndex().any { (j, b) ->
                    i != j && b.containsRect(a) && (!a.containsRect(b) || j < i)
                }
                if (!contained) free += a
            }
        }
    }

    private class Candidate(val bin: Bin, val rect: RectMm, val score1: Double, val score2: Double) {
        fun better(other: Candidate): Boolean = when {
            score1 < other.score1 - EPSILON -> true
            score1 > other.score1 + EPSILON -> false
            score2 < other.score2 - EPSILON -> true
            score2 > other.score2 + EPSILON -> false
            rect.y < other.rect.y - EPSILON -> true
            rect.y > other.rect.y + EPSILON -> false
            else -> rect.x < other.rect.x - EPSILON
        }
    }

    fun pack(
        items: List<LayoutItem>,
        areaW: Double,
        areaH: Double,
        gap: Double,
        allowRotation: Boolean,
        heuristic: Heuristic,
    ): Packed {
        val sorted = items.sortedWith(
            compareByDescending<LayoutItem> { it.size.area() }
                .thenByDescending { max(it.size.width, it.size.height) }
                .thenBy { it.order }
                .thenBy { it.copy },
        )
        val binW = areaW + gap
        val binH = areaH + gap
        val bins = mutableListOf<Bin>()
        val placements = mutableListOf<Placement>()
        val unplaced = mutableListOf<LayoutItem>()

        for (item in sorted) {
            val orientations = buildList {
                if (fits(item.size.width, item.size.height, areaW, areaH)) add(false)
                if (allowRotation && fits(item.size.height, item.size.width, areaW, areaH)) add(true)
            }
            if (orientations.isEmpty()) {
                unplaced += item
                continue
            }
            fun search(bin: Bin): Pair<Candidate, Boolean>? {
                var best: Pair<Candidate, Boolean>? = null
                for (rotated in orientations) {
                    val w = (if (rotated) item.size.height else item.size.width) + gap
                    val h = (if (rotated) item.size.width else item.size.height) + gap
                    val c = bin.find(w, h, heuristic) ?: continue
                    if (best == null || c.better(best.first)) best = c to rotated
                }
                return best
            }

            var found: Pair<Candidate, Boolean>? = null
            for (bin in bins) {
                found = search(bin)
                if (found != null) break
            }
            if (found == null) {
                val bin = Bin(bins.size, binW, binH).also { bins += it }
                found = search(bin)
            }
            if (found == null) {
                unplaced += item
                continue
            }
            val (candidate, rotated) = found
            candidate.bin.place(candidate.rect)
            val w = if (rotated) item.size.height else item.size.width
            val h = if (rotated) item.size.width else item.size.height
            placements += Placement(item.key, item.photoId, candidate.bin.index, RectMm(candidate.rect.x, candidate.rect.y, w, h), rotated)
        }
        return Packed(placements, bins.size, unplaced)
    }
}
