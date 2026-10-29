package io.github.riyadmondol2006.idsheetstudio.pdf

import io.github.riyadmondol2006.idsheetstudio.core.mmToPoints
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetPage
import io.github.riyadmondol2006.idsheetstudio.domain.layout.Stroke

/** Converts device-independent [SheetPage]s (mm, top-left origin) into PDF pages (pt, bottom-left). */
object SheetPdfEncoder {

    fun writePage(writer: PdfWriter, page: SheetPage, images: Map<String, PdfWriter.ImageRef>) {
        val pageW = mmToPoints(page.size.width)
        val pageH = mmToPoints(page.size.height)
        writer.addPage(pageW, pageH) {
            var current: Stroke? = null
            fun stroke(s: Stroke) {
                if (s != current) {
                    setStroke(mmToPoints(s.widthMm), s.color, s.dashPattern?.map { mmToPoints(it) }?.toDoubleArray(), s.roundCap)
                    current = s
                }
            }
            for (op in page.ops) when (op) {
                is SheetOp.Image -> {
                    val ref = images[op.photoId] ?: error("Missing image for ${op.photoId}")
                    val r = op.rect
                    drawImage(
                        ref,
                        x = mmToPoints(r.x),
                        y = pageH - mmToPoints(r.y + r.height),
                        w = mmToPoints(r.width),
                        h = mmToPoints(r.height),
                        rotateClockwise = op.rotated,
                    )
                }
                is SheetOp.StrokeRect -> {
                    stroke(op.stroke)
                    val r = op.rect
                    strokeRect(mmToPoints(r.x), pageH - mmToPoints(r.y + r.height), mmToPoints(r.width), mmToPoints(r.height))
                }
                is SheetOp.Line -> {
                    stroke(op.stroke)
                    strokeLine(mmToPoints(op.x1), pageH - mmToPoints(op.y1), mmToPoints(op.x2), pageH - mmToPoints(op.y2))
                }
            }
        }
    }
}
