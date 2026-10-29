package io.github.riyadmondol2006.idsheetstudio.domain.model

import io.github.riyadmondol2006.idsheetstudio.core.MM_PER_INCH
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm

data class PaperSize(val id: String, val name: String, val size: SizeMm)

object PaperSizes {
    val A4 = PaperSize("a4", "A4", SizeMm(210.0, 297.0))
    val A5 = PaperSize("a5", "A5", SizeMm(148.0, 210.0))
    val LETTER = PaperSize("letter", "Letter", SizeMm(8.5 * MM_PER_INCH, 11.0 * MM_PER_INCH))
    val LEGAL = PaperSize("legal", "Legal", SizeMm(8.5 * MM_PER_INCH, 14.0 * MM_PER_INCH))
    val PHOTO_4X6 = PaperSize("photo_4x6", "4 × 6 in", SizeMm(4.0 * MM_PER_INCH, 6.0 * MM_PER_INCH))
    val PHOTO_5X7 = PaperSize("photo_5x7", "5 × 7 in", SizeMm(5.0 * MM_PER_INCH, 7.0 * MM_PER_INCH))

    const val CUSTOM_ID = "custom"

    val all = listOf(A4, A5, LETTER, LEGAL, PHOTO_4X6, PHOTO_5X7)

    fun byId(id: String?): PaperSize? = all.firstOrNull { it.id == id }
}
