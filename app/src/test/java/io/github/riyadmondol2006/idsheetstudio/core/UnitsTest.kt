package io.github.riyadmondol2006.idsheetstudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitsTest {
    @Test fun inchConversionIsExact() {
        assertEquals(25.4, LengthUnit.INCH.toMm(1.0), 0.0)
        assertEquals(2.0, LengthUnit.INCH.fromMm(50.8), 1e-12)
        assertEquals(3.5, LengthUnit.CM.fromMm(35.0), 1e-12)
    }

    @Test fun pointsConversion() {
        assertEquals(595.2756, mmToPoints(210.0), 1e-4)
        assertEquals(841.8898, mmToPoints(297.0), 1e-4)
        assertEquals(72.0, mmToPoints(25.4), 1e-12)
        assertEquals(210.0, pointsToMm(mmToPoints(210.0)), 1e-12)
    }

    @Test fun pixelsAtDpi() {
        assertEquals(827, mmToPixels(35.0, 600))
        assertEquals(1063, mmToPixels(45.0, 600))
        assertEquals(600, mmToPixels(25.4, 600))
    }

    @Test fun formatting() {
        assertEquals("35", formatDecimal(35.0, 1))
        assertEquals("2", formatDecimal(50.8 / 25.4, 3))
        assertEquals("0.5", formatDecimal(0.5, 2))
        assertEquals("-1.25", formatDecimal(-1.25, 2))
        assertEquals("0", formatDecimal(-0.0001, 2))
        assertEquals("35 mm", LengthUnit.MM.format(35.0))
        assertEquals("1.378 in", LengthUnit.INCH.format(35.0))
    }

    @Test fun parsing() {
        assertEquals(3.5, parseDecimal("3,5")!!, 0.0)
        assertEquals(12.0, parseDecimal(" 12 ")!!, 0.0)
        assertNull(parseDecimal("abc"))
        assertNull(parseDecimal(""))
    }
}
