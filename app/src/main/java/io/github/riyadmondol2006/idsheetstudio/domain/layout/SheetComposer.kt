package io.github.riyadmondol2006.idsheetstudio.domain.layout

import io.github.riyadmondol2006.idsheetstudio.core.RectMm
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.model.BorderSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides
import io.github.riyadmondol2006.idsheetstudio.domain.model.LineStyle
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import kotlin.math.max

/**
 * Device-independent drawing operations for one sheet, in millimetres with a top-left origin.
 * Both the on-screen preview and the PDF writer render exactly these operations, so the preview
 * always matches the printed output.
 */
sealed interface SheetOp {
    /** A photo copy. When [rotated] the photo is turned 90° clockwise to fill [rect]. */
    data class Image(val photoId: String, val key: String, val rect: RectMm, val rotated: Boolean) : SheetOp

    /** A stroked rectangle; [rect] is the centre line of the stroke. */
    data class StrokeRect(val rect: RectMm, val stroke: Stroke) : SheetOp

    data class Line(val x1: Double, val y1: Double, val x2: Double, val y2: Double, val stroke: Stroke) : SheetOp
}

data class Stroke(val widthMm: Double, val color: Long, val style: LineStyle) {
    /** Dash pattern in millimetres (on, off), or null for a solid line. */
    val dashPattern: DoubleArray?
        get() = when (style) {
            LineStyle.SOLID -> null
            LineStyle.DASHED -> doubleArrayOf(max(1.5, widthMm * 4), max(1.0, widthMm * 2.5))
            LineStyle.DOTTED -> doubleArrayOf(0.0, max(0.6, widthMm * 2.2))
        }

    /** Dotted lines are drawn as zero-length dashes with round caps. */
    val roundCap: Boolean get() = style == LineStyle.DOTTED
}

data class SheetPage(val index: Int, val size: SizeMm, val ops: List<SheetOp>)

object SheetComposer {
    const val CROP_MARK_LENGTH_MM = 3.0
    const val CROP_MARK_OFFSET_MM = 0.6
    const val GUIDE_WIDTH_MM = 0.15
    const val TRIM_TAIL_MM = 5.0

    fun compose(layout: LayoutResult, settings: SheetSettings): List<SheetPage> =
        (0 until layout.pageCount).map { page ->
            composePage(page, layout.pageSize, layout.placementsOn(page), settings)
        }

    fun composePage(index: Int, pageSize: SizeMm, placements: List<Placement>, settings: SheetSettings): SheetPage {
        val ops = mutableListOf<SheetOp>()
        val page = RectMm(0.0, 0.0, pageSize.width, pageSize.height)

        // Guides go underneath the photos so they never cover image content.
        if (settings.cutGuides == CutGuides.CROP_MARKS) {
            val stroke = Stroke(GUIDE_WIDTH_MM, settings.guideColor, LineStyle.SOLID)
            placements.forEach { ops += cropMarks(it.rect, page, stroke) }
        }
        if (settings.cutGuides == CutGuides.TRIM_LINES) {
            ops += trimLines(placements, page, Stroke(GUIDE_WIDTH_MM, settings.guideColor, LineStyle.DASHED))
        }

        placements.forEach { ops += SheetOp.Image(it.photoId, it.key, it.rect, it.rotated) }

        if (settings.photoBorder.enabled) {
            placements.forEach { p -> borderOp(p.rect, settings.photoBorder)?.let { ops += it } }
        }

        if (settings.cutGuides == CutGuides.OUTLINES) {
            val stroke = Stroke(GUIDE_WIDTH_MM, settings.guideColor, LineStyle.DASHED)
            placements.forEach { ops += SheetOp.StrokeRect(it.rect, stroke) }
        }

        if (settings.pageBorder.enabled) {
            borderOp(page, settings.pageBorder)?.let { ops += it }
        }
        return SheetPage(index, pageSize, ops)
    }

    /** A border lying entirely inside [bounds], starting [BorderSettings.insetMm] from the edge. */
    fun borderOp(bounds: RectMm, border: BorderSettings): SheetOp.StrokeRect? {
        val width = border.thicknessMm
        if (width <= 0.0) return null
        val centre = bounds.inset(border.insetMm.coerceAtLeast(0.0) + width / 2)
        if (centre.width <= 0.0 || centre.height <= 0.0) return null
        return SheetOp.StrokeRect(centre, Stroke(width, border.color, border.style))
    }

    /**
     * Cutting lines for straight cuts with a ruler, scissors or a trimmer, drawn below the photos
     * so they only show in the gaps and margins:
     * - every distinct top/bottom photo edge gets a horizontal line across the full page width
     *   (the first cuts, which split the sheet into strips);
     * - within each strip (a band of photos that overlap vertically), every left/right photo edge
     *   gets a vertical line from the cut above the strip to the cut below it, so lines don't
     *   clutter empty parts of the page. The last strip's lines end [TRIM_TAIL_MM] below it.
     */
    fun trimLines(placements: List<Placement>, page: RectMm, stroke: Stroke): List<SheetOp.Line> {
        if (placements.isEmpty()) return emptyList()
        fun distinct(values: List<Double>) = values.sorted().fold(mutableListOf<Double>()) { acc, v ->
            if (acc.isEmpty() || v - acc.last() > 1e-6) acc += v
            acc
        }
        val horizontal = distinct(placements.flatMap { listOf(it.rect.y, it.rect.bottom) })
            .map { SheetOp.Line(page.x, it, page.right, it, stroke) }

        // Strips: union of the photos' vertical extents.
        val bands = mutableListOf<MutableList<Placement>>()
        var bandBottom = Double.NEGATIVE_INFINITY
        for (p in placements.sortedBy { it.rect.y }) {
            if (bands.isEmpty() || p.rect.y >= bandBottom - 1e-6) {
                bands += mutableListOf(p)
                bandBottom = p.rect.bottom
            } else {
                bands.last() += p
                bandBottom = maxOf(bandBottom, p.rect.bottom)
            }
        }
        val vertical = bands.flatMapIndexed { index, band ->
            val top = band.minOf { it.rect.y }
            val bottom = band.maxOf { it.rect.bottom }
            val from = if (index == 0) page.y else bands[index - 1].maxOf { it.rect.bottom }
            val to = if (index == bands.lastIndex) minOf(page.bottom, bottom + TRIM_TAIL_MM) else bands[index + 1].minOf { it.rect.y }
            distinct(band.flatMap { listOf(it.rect.x, it.rect.right) }).map { x ->
                SheetOp.Line(x, minOf(from, top), x, maxOf(to, bottom), stroke)
            }
        }
        return horizontal + vertical
    }

    private fun cropMarks(r: RectMm, page: RectMm, stroke: Stroke): List<SheetOp.Line> {
        val o = CROP_MARK_OFFSET_MM
        val l = CROP_MARK_LENGTH_MM
        val lines = listOf(
            // top-left
            SheetOp.Line(r.x - o - l, r.y, r.x - o, r.y, stroke),
            SheetOp.Line(r.x, r.y - o - l, r.x, r.y - o, stroke),
            // top-right
            SheetOp.Line(r.right + o, r.y, r.right + o + l, r.y, stroke),
            SheetOp.Line(r.right, r.y - o - l, r.right, r.y - o, stroke),
            // bottom-left
            SheetOp.Line(r.x - o - l, r.bottom, r.x - o, r.bottom, stroke),
            SheetOp.Line(r.x, r.bottom + o, r.x, r.bottom + o + l, stroke),
            // bottom-right
            SheetOp.Line(r.right + o, r.bottom, r.right + o + l, r.bottom, stroke),
            SheetOp.Line(r.right, r.bottom + o, r.right, r.bottom + o + l, stroke),
        )
        return lines.mapNotNull { clip(it, page) }
    }

    private fun clip(line: SheetOp.Line, page: RectMm): SheetOp.Line? {
        val x1 = line.x1.coerceIn(page.x, page.right)
        val x2 = line.x2.coerceIn(page.x, page.right)
        val y1 = line.y1.coerceIn(page.y, page.bottom)
        val y2 = line.y2.coerceIn(page.y, page.bottom)
        if (x1 == x2 && y1 == y2) return null
        return line.copy(x1 = x1, y1 = y1, x2 = x2, y2 = y2)
    }
}
