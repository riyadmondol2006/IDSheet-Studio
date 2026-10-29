package io.github.riyadmondol2006.idsheetstudio.image

import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.hypot

class FaceAlignMathTest {
    /** Checks, through CropMath.matrix, where the eyes land in the frame. */
    private fun check(srcW: Double, srcH: Double, frameW: Double, frameH: Double, eyeX: Double, eyeY: Double, eyeDistance: Double): PhotoTransform {
        val t = FaceAlignMath.align(srcW, srcH, frameW, frameH, eyeX, eyeY, eyeDistance)!!
        assertEquals(0.0, t.rotation, 0.0)
        val m = CropMath.matrix(srcW, srcH, frameW, frameH, t)
        val (mx, my) = CropMath.apply(m, eyeX, eyeY)
        assertEquals(0.5 * frameW, mx, 1e-6)
        assertEquals(0.42 * frameH, my, 1e-6)
        val (lx, ly) = CropMath.apply(m, eyeX - eyeDistance / 2, eyeY)
        val (rx, ry) = CropMath.apply(m, eyeX + eyeDistance / 2, eyeY)
        if (t.zoom > PhotoTransform.MIN_ZOOM && t.zoom < PhotoTransform.MAX_ZOOM) {
            assertEquals(FaceAlignMath.EYE_DISTANCE * frameW, hypot(rx - lx, ry - ly), 1e-6)
        }
        return t
    }

    @Test fun placesEyesForPassportFrame() {
        val t = check(3000.0, 4000.0, 35.0, 45.0, eyeX = 1400.0, eyeY = 1500.0, eyeDistance = 420.0)
        // Cover scale is 35/3000, so zoom = EYE_DISTANCE * 35 / 420 / (35/3000).
        assertEquals(FaceAlignMath.EYE_DISTANCE * 3000 / 420, t.zoom, 1e-9)
    }

    @Test fun resolutionIndependent() {
        val a = check(3000.0, 4000.0, 35.0, 45.0, 1400.0, 1500.0, 420.0)
        val b = check(750.0, 1000.0, 350.0, 450.0, 350.0, 375.0, 105.0)
        assertEquals(a.zoom, b.zoom, 1e-9)
        assertEquals(a.offsetX, b.offsetX, 1e-9)
        assertEquals(a.offsetY, b.offsetY, 1e-9)
    }

    @Test fun landscapeSourceAndOffCentreFace() {
        check(4000.0, 3000.0, 35.0, 45.0, eyeX = 3100.0, eyeY = 900.0, eyeDistance = 260.0)
        check(1200.0, 800.0, 51.0, 51.0, eyeX = 300.0, eyeY = 600.0, eyeDistance = 150.0)
    }

    @Test fun zoomIsClampedButEyesStillPlaced() {
        // A tiny face would need more than MAX_ZOOM; a huge one less than MIN_ZOOM.
        assertEquals(PhotoTransform.MAX_ZOOM, check(3000.0, 4000.0, 35.0, 45.0, 1500.0, 2000.0, 5.0).zoom, 0.0)
        assertEquals(PhotoTransform.MIN_ZOOM, check(3000.0, 4000.0, 35.0, 45.0, 1500.0, 2000.0, 2900.0).zoom, 0.0)
    }

    @Test fun rejectsDegenerateInput() {
        assertNull(FaceAlignMath.align(0.0, 100.0, 35.0, 45.0, 10.0, 10.0, 5.0))
        assertNull(FaceAlignMath.align(100.0, 100.0, 35.0, 0.0, 10.0, 10.0, 5.0))
        assertNull(FaceAlignMath.align(100.0, 100.0, 35.0, 45.0, 10.0, 10.0, 0.0))
        assertNull(FaceAlignMath.align(100.0, 100.0, 35.0, 45.0, Double.NaN, 10.0, 5.0))
    }
}
