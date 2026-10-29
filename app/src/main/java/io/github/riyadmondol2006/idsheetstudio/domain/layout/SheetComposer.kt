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
    const val GUIDE_COLOR = 0xFF7A7A7AL
    const val OUTLINE_COLOR = 0xFFA0A0A0L

    fun compose(layout: LayoutResult, settings: SheetSettings): List<SheetPage> =
        (0 until layout.pageCount).map { page ->
            composePage(page, layout.pageSize, layout.placementsOn(page), settings)
        }

    fun composePage(index: Int, pageSize: SizeMm, placements: List<Placement>, settings: SheetSettings): SheetPage {
        val ops = mutableListOf<SheetOp>()
        val page = RectMm(0.0, 0.0, pageSize.width, pageSize.height)

        // Crop marks go underneath the photos so they never cover image content.
        if (settings.cutGuides == CutGuides.CROP_MARKS) {
            val stroke = Stroke(GUIDE_WIDTH_MM, GUIDE_COLOR, LineStyle.SOLID)
            placements.forEach { ops += cropMarks(it.rect, page, stroke) }
        }

        placements.forEach { ops += SheetOp.Image(it.photoId, it.key, it.rect, it.rotated) }

        if (settings.photoBorder.enabled) {
            placements.forEach { p -> borderOp(p.rect, settings.photoBorder)?.let { ops += it } }
        }

        if (settings.cutGuides == CutGuides.OUTLINES) {
            val stroke = Stroke(GUIDE_WIDTH_MM, OUTLINE_COLOR, LineStyle.DASHED)
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
