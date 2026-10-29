package io.github.riyadmondol2006.idsheetstudio.ui.sheet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.riyadmondol2006.idsheetstudio.core.RectMm
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetOp
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetPage
import kotlin.math.max
import kotlin.math.roundToInt

/** Drag result in page millimetres. */
data class MoveRequest(val key: String, val page: Int, val x: Double, val y: Double)

/**
 * Draws a [SheetPage] to scale. Long-press and drag a photo to reposition it; [onMove] receives
 * the proposed top-left position in millimetres (snapped to 0.5 mm).
 */
@Composable
fun SheetCanvas(
    page: SheetPage,
    images: Map<String, ImageBitmap>,
    modifier: Modifier = Modifier,
    printableArea: RectMm? = null,
    showPrintableArea: Boolean = false,
    onMove: ((MoveRequest) -> Unit)? = null,
) {
    val aspect = (page.size.width / page.size.height).toFloat()
    val outline = MaterialTheme.colorScheme.primary
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val currentPage by rememberUpdatedState(page)
    val currentOnMove by rememberUpdatedState(onMove)

    Surface(
        modifier = modifier.fillMaxWidth().aspectRatio(aspect).padding(2.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        shadowElevation = 6.dp,
    ) {
        Box {
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .semantics { contentDescription = "Sheet page ${page.index + 1}" }
                    .then(
                        if (onMove == null) Modifier else Modifier.pointerInput(page.index) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { pos ->
                                    val scale = size.width / currentPage.size.width.toFloat()
                                    val xMm = pos.x / scale
                                    val yMm = pos.y / scale
                                    dragKey = currentPage.ops.filterIsInstance<SheetOp.Image>()
                                        .lastOrNull { it.rect.containsPoint(xMm.toDouble(), yMm.toDouble()) }?.key
                                    dragOffset = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    if (dragKey != null) {
                                        change.consume()
                                        dragOffset += amount
                                    }
                                },
                                onDragEnd = {
                                    val key = dragKey
                                    val op = currentPage.ops.filterIsInstance<SheetOp.Image>().firstOrNull { it.key == key }
                                    if (key != null && op != null) {
                                        val scale = size.width / currentPage.size.width.toFloat()
                                        val snap = { v: Double -> (v * 2).roundToInt() / 2.0 }
                                        currentOnMove?.invoke(
                                            MoveRequest(
                                                key,
                                                currentPage.index,
                                                snap(op.rect.x + dragOffset.x / scale),
                                                snap(op.rect.y + dragOffset.y / scale),
                                            ),
                                        )
                                    }
                                    dragKey = null
                                    dragOffset = Offset.Zero
                                },
                                onDragCancel = {
                                    dragKey = null
                                    dragOffset = Offset.Zero
                                },
                            )
                        },
                    ),
            ) {
                val scale = size.width / page.size.width.toFloat()
                if (showPrintableArea && printableArea != null) {
                    drawRect(
                        color = outline.copy(alpha = 0.35f),
                        topLeft = Offset(printableArea.x.toFloat() * scale, printableArea.y.toFloat() * scale),
                        size = Size(printableArea.width.toFloat() * scale, printableArea.height.toFloat() * scale),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))),
                    )
                }
                for (op in page.ops) {
                    if (op is SheetOp.Image && op.key == dragKey) continue
                    drawOp(op, images, scale)
                }
                // The dragged photo floats above everything with a highlight.
                val dragged = page.ops.filterIsInstance<SheetOp.Image>().firstOrNull { it.key == dragKey }
                if (dragged != null) {
                    withTransform({ translate(dragOffset.x, dragOffset.y) }) {
                        drawOp(dragged, images, scale)
                        drawRect(
                            outline,
                            topLeft = Offset(dragged.rect.x.toFloat() * scale, dragged.rect.y.toFloat() * scale),
                            size = Size(dragged.rect.width.toFloat() * scale, dragged.rect.height.toFloat() * scale),
                            style = Stroke(width = 2.dp.toPx()),
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawOp(op: SheetOp, images: Map<String, ImageBitmap>, scale: Float) {
    when (op) {
        is SheetOp.Image -> {
            val r = op.rect
            val left = r.x.toFloat() * scale
            val top = r.y.toFloat() * scale
            val w = r.width.toFloat() * scale
            val h = r.height.toFloat() * scale
            val bitmap = images[op.photoId]
            if (bitmap == null) {
                drawRect(Color(0xFFE6E8EE), Offset(left, top), Size(w, h))
                return
            }
            if (op.rotated) {
                // Draw the upright photo (h × w on screen) rotated 90° clockwise about the centre.
                val cx = left + w / 2
                val cy = top + h / 2
                rotate(90f, Offset(cx, cy)) {
                    drawImage(
                        bitmap,
                        dstOffset = IntOffset((cx - h / 2).roundToInt(), (cy - w / 2).roundToInt()),
                        dstSize = IntSize(h.roundToInt(), w.roundToInt()),
                    )
                }
            } else {
                drawImage(bitmap, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = IntSize(w.roundToInt(), h.roundToInt()))
            }
        }
        is SheetOp.StrokeRect -> {
            val r = op.rect
            drawRect(
                color = Color(op.stroke.color.toInt()),
                topLeft = Offset(r.x.toFloat() * scale, r.y.toFloat() * scale),
                size = Size(r.width.toFloat() * scale, r.height.toFloat() * scale),
                style = stroke(op.stroke, scale),
            )
        }
        is SheetOp.Line -> {
            val s = stroke(op.stroke, scale)
            drawLine(
                color = Color(op.stroke.color.toInt()),
                start = Offset(op.x1.toFloat() * scale, op.y1.toFloat() * scale),
                end = Offset(op.x2.toFloat() * scale, op.y2.toFloat() * scale),
                strokeWidth = s.width,
                cap = s.cap,
                pathEffect = s.pathEffect,
            )
        }
    }
}

private fun stroke(stroke: io.github.riyadmondol2006.idsheetstudio.domain.layout.Stroke, scale: Float): Stroke {
    val width = max(1f, stroke.widthMm.toFloat() * scale)
    val dash = stroke.dashPattern?.let { pattern ->
        val on = max(0.01f, pattern[0].toFloat() * scale)
        val off = max(width + 1f, pattern[1].toFloat() * scale)
        PathEffect.dashPathEffect(floatArrayOf(on, off))
    }
    return Stroke(width = width, cap = if (stroke.roundCap) StrokeCap.Round else StrokeCap.Butt, pathEffect = dash)
}
