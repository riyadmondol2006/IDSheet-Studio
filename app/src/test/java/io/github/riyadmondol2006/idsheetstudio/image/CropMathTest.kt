package io.github.riyadmondol2006.idsheetstudio.image

import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import org.junit.Assert.assertEquals
import org.junit.Test

class CropMathTest {
    @Test fun identityCoversFrameAndCentres() {
        // 3000 x 4000 source into a 350 x 450 frame: cover scale = max(350/3000, 450/4000) = 0.1167.
        val m = CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, PhotoTransform.Identity)
        val (cx, cy) = CropMath.apply(m, 1500.0, 2000.0)
        assertEquals(175.0, cx, 1e-9)
        assertEquals(225.0, cy, 1e-9)
        val (left, top) = CropMath.apply(m, 0.0, 0.0)
        val (right, bottom) = CropMath.apply(m, 3000.0, 4000.0)
        assertEquals(0.0, left, 1e-9) // width-limited: exactly fills horizontally
        assertEquals(350.0, right, 1e-9)
        assertTrue(top <= 0.0 && bottom >= 450.0)
    }

    @Test fun resultIsResolutionIndependent() {
        val t = PhotoTransform(zoom = 1.7, offsetX = 0.1, offsetY = -0.2, rotation = 12.0)
        val small = CropMath.matrix(1000.0, 1000.0, 35.0, 45.0, t)
        val large = CropMath.matrix(1000.0, 1000.0, 3500.0, 4500.0, t)
        val (sx, sy) = CropMath.apply(small, 250.0, 700.0)
        val (lx, ly) = CropMath.apply(large, 250.0, 700.0)
        assertEquals(sx * 100, lx, 1e-6)
        assertEquals(sy * 100, ly, 1e-6)
    }

    @Test fun rotationIsAroundImageCentre() {
        val m = CropMath.matrix(200.0, 100.0, 100.0, 100.0, PhotoTransform(rotation = 90.0))
        val (cx, cy) = CropMath.apply(m, 100.0, 50.0)
        assertEquals(50.0, cx, 1e-9)
        assertEquals(50.0, cy, 1e-9)
        // The top-left source corner rotates clockwise to the top-right.
        val (x, _) = CropMath.apply(m, 0.0, 0.0)
        assertTrue(x > 50.0)
    }

    @Test fun pinchKeepsCentroidFixed() {
        val t = PhotoTransform(zoom = 1.0, offsetX = 0.0, offsetY = 0.0)
        val before = CropMath.matrix(1000.0, 1000.0, 300.0, 400.0, t)
        // Source point currently under (60, 80):
        val inv = invert(before)
        val (srcX, srcY) = CropMath.apply(inv, 60.0, 80.0)
        val after = CropMath.applyGesture(t, 300.0, 400.0, 60.0, 80.0, 0.0, 0.0, 2.0, 0.0)
        val m2 = CropMath.matrix(1000.0, 1000.0, 300.0, 400.0, after)
        val (x, y) = CropMath.apply(m2, srcX, srcY)
        assertEquals(60.0, x, 1e-6)
        assertEquals(80.0, y, 1e-6)
        assertEquals(2.0, after.zoom, 1e-12)
    }

    @Test fun zoomIsClamped() {
        val t = CropMath.applyGesture(PhotoTransform(), 100.0, 100.0, 50.0, 50.0, 0.0, 0.0, 100.0, 0.0)
        assertEquals(PhotoTransform.MAX_ZOOM, t.zoom, 0.0)
    }

    @Test fun degreesNormalise() {
        assertEquals(-90.0, CropMath.normalizeDegrees(270.0), 0.0)
        assertEquals(180.0, CropMath.normalizeDegrees(-180.0), 0.0)
        assertEquals(10.0, CropMath.normalizeDegrees(370.0), 0.0)
    }

    @Test fun coverScaleAtZeroRotationIsUnchanged() {
        assertEquals(kotlin.math.max(350.0 / 3000, 450.0 / 4000), CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0), 1e-15)
        assertEquals(CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0), CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0, 0.0), 0.0)
        // A half turn covers exactly like no rotation.
        assertEquals(CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0), CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0, 180.0), 1e-12)
    }

    @Test fun quarterTurnStillCoversFrame() {
        // Rotated, the 3000 x 4000 source is 4000 wide and 3000 tall: scale = max(350/4000, 450/3000) = 0.15.
        assertEquals(0.15, CropMath.coverScale(3000.0, 4000.0, 350.0, 450.0, 90.0), 1e-12)
        assertFrameCornersInsideSource(PhotoTransform(rotation = 90.0))
        assertFrameCornersInsideSource(PhotoTransform(rotation = -90.0))
    }

    @Test fun straightenStillCoversFrame() {
        assertFrameCornersInsideSource(PhotoTransform(rotation = 7.0))
        assertFrameCornersInsideSource(PhotoTransform(rotation = -7.0))
        assertFrameCornersInsideSource(PhotoTransform(rotation = 97.0))
        assertFrameCornersInsideSource(PhotoTransform(rotation = 45.0))
    }

    @Test fun coverIsTight() {
        // The scale is the smallest that covers: at least one frame corner touches the image edge.
        for (rotation in listOf(0.0, 7.0, 90.0, -33.0)) {
            val m = CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, PhotoTransform(rotation = rotation))
            val inv = invert(m)
            val margin = frameCorners().minOf { (x, y) ->
                val (sx, sy) = CropMath.apply(inv, x, y)
                minOf(sx, 3000.0 - sx, sy, 4000.0 - sy)
            }
            assertEquals("rotation $rotation", 0.0, margin, 1e-6)
        }
    }

    @Test fun twistKeepsCentroidFixed() {
        val t = PhotoTransform(zoom = 1.3, offsetX = 0.05, offsetY = -0.1, rotation = 3.0)
        val before = CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, t)
        val (srcX, srcY) = CropMath.apply(invert(before), 120.0, 200.0)
        val after = CropMath.applyGesture(t, 350.0, 450.0, 120.0, 200.0, 0.0, 0.0, 1.1, 25.0, srcW = 3000.0, srcH = 4000.0)
        val (x, y) = CropMath.apply(CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, after), srcX, srcY)
        assertEquals(120.0, x, 1e-6)
        assertEquals(200.0, y, 1e-6)
        assertEquals(1.43, after.zoom, 1e-12)
        assertEquals(28.0, after.rotation, 1e-12)
    }

    private fun frameCorners() = listOf(0.0 to 0.0, 350.0 to 0.0, 0.0 to 450.0, 350.0 to 450.0)

    /** Every corner of a 350 x 450 frame maps back to a point inside the 3000 x 4000 source. */
    private fun assertFrameCornersInsideSource(t: PhotoTransform) {
        val inv = invert(CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, t))
        for ((x, y) in frameCorners()) {
            val (sx, sy) = CropMath.apply(inv, x, y)
            val eps = 1e-6
            assertTrue("rotation ${t.rotation}: ($x, $y) -> ($sx, $sy)", sx >= -eps && sx <= 3000 + eps && sy >= -eps && sy <= 4000 + eps)
        }
    }

    private fun assertTrue(message: String, condition: Boolean) = org.junit.Assert.assertTrue(message, condition)

    private fun assertTrue(condition: Boolean) = org.junit.Assert.assertTrue(condition)

    private fun invert(m: DoubleArray): DoubleArray {
        val det = m[0] * m[4] - m[1] * m[3]
        val a = m[4] / det
        val b = -m[1] / det
        val d = -m[3] / det
        val e = m[0] / det
        return doubleArrayOf(a, b, -(a * m[2] + b * m[5]), d, e, -(d * m[2] + e * m[5]))
    }

    @Test fun photoCannotBeDraggedOutOfTheFrame() {
        // Regression: three huge drags used to push the photo completely out of the frame.
        var t = PhotoTransform()
        repeat(3) { t = CropMath.applyGesture(t, 350.0, 450.0, 175.0, 225.0, 0.0, 900.0, 1.0, 0.0, 3000.0, 4000.0) }
        val m = CropMath.matrix(3000.0, 4000.0, 350.0, 450.0, t)
        val (_, top) = CropMath.apply(m, 1500.0, 0.0)
        // The photo's top edge may come down at most to the frame centre.
        assertTrue("top=$top", top <= 225.0 + 1e-6)
        // Same for a rotated, zoomed-out photo dragged sideways.
        var r = PhotoTransform(zoom = 0.5, rotation = 90.0)
        repeat(5) { r = CropMath.applyGesture(r, 350.0, 450.0, 175.0, 225.0, -2000.0, 0.0, 1.0, 0.0, 3000.0, 4000.0) }
        assertTrue(r.offsetX.isFinite() && r.offsetX > -1.0)
    }
}
