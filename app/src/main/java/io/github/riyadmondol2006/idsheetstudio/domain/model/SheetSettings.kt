package io.github.riyadmondol2006.idsheetstudio.domain.model

import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.Margins
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import kotlinx.serialization.Serializable

@Serializable
enum class Orientation { PORTRAIT, LANDSCAPE }

@Serializable
enum class LineStyle { SOLID, DASHED, DOTTED }

@Serializable
enum class CutGuides {
    NONE,
    /** Short marks at each photo corner. */
    CROP_MARKS,
    /** A thin dashed outline around each photo. */
    OUTLINES,
    /** Full-length lines across the page along every photo edge, for straight cuts. */
    TRIM_LINES,
}

@Serializable
enum class LayoutStrategy { AUTO, ROWS, DENSE }

/** Where each page's block of photos sits horizontally inside the printable area. */
@Serializable
enum class HorizontalAlign { START, CENTER, END }

/** Where each page's block of photos sits vertically inside the printable area. */
@Serializable
enum class VerticalAlign { TOP, CENTER, BOTTOM }

/**
 * A border drawn either around the page or around each photo.
 *
 * @param thicknessMm line thickness. The border always lies fully inside the bounds it decorates,
 *   so it never changes the physical size of a photo or overlaps a neighbour.
 * @param insetMm distance between the outer edge of the bounds and the outer edge of the line.
 */
@Serializable
data class BorderSettings(
    val enabled: Boolean = false,
    val thicknessMm: Double = 0.3,
    val color: Long = 0xFF000000,
    val style: LineStyle = LineStyle.SOLID,
    val insetMm: Double = 0.0,
)

@Serializable
data class SheetSettings(
    val paperId: String = PaperSizes.A4.id,
    val customPaper: SizeMm = SizeMm(210.0, 297.0),
    val orientation: Orientation = Orientation.PORTRAIT,
    val margins: Margins = Margins.all(5.0),
    val spacingMm: Double = 2.0,
    val pageBorder: BorderSettings = BorderSettings(insetMm = 3.0),
    val photoBorder: BorderSettings = BorderSettings(thicknessMm = 0.2, color = 0xFF9E9E9E),
    val cutGuides: CutGuides = CutGuides.TRIM_LINES,
    /** Colour of crop marks, outlines and cutting lines. */
    val guideColor: Long = 0xFF7A7A7A,
    val strategy: LayoutStrategy = LayoutStrategy.AUTO,
    val allowRotation: Boolean = false,
    /** Horizontal position of each page's content block. START is the left edge of the paper. */
    val horizontalAlign: HorizontalAlign = HorizontalAlign.CENTER,
    /** Vertical position of each page's content block. */
    val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    /**
     * Spread photos over the printable area instead of keeping them packed together. See
     * [io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine.layout] for the exact rules.
     */
    val spreadEvenly: Boolean = false,
    val displayUnit: LengthUnit = LengthUnit.MM,
    /** Manual position overrides keyed by placement key ("photoId#copy"). */
    val manualPositions: Map<String, ManualPosition> = emptyMap(),
) {
    /** Paper size with orientation applied. */
    val pageSize: SizeMm
        get() {
            val base = PaperSizes.byId(paperId)?.size ?: customPaper
            val portrait = if (base.width <= base.height) base else base.rotated()
            return if (orientation == Orientation.PORTRAIT) portrait else portrait.rotated()
        }

    val paperName: String get() = PaperSizes.byId(paperId)?.name ?: "Custom"
}

@Serializable
data class ManualPosition(val page: Int, val x: Double, val y: Double)
