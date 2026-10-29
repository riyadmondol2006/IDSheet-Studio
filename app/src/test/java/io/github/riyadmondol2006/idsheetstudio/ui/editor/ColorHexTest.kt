package io.github.riyadmondol2006.idsheetstudio.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ColorHexTest {
    @Test fun parsesWithOrWithoutHash() {
        assertEquals(0xFF1E5BD8, parseHexColor("#1E5BD8"))
        assertEquals(0xFF1E5BD8, parseHexColor("1e5bd8"))
        assertEquals(0xFFFFFFFF, parseHexColor("  #ffffff "))
        assertEquals(0xFF000000, parseHexColor("000000"))
    }

    @Test fun rejectsInvalid() {
        for (bad in listOf("", "#", "#FFF", "#FFFFFFF", "#GGGGGG", "##FFFFFF", "12 456", "-12345", "+12345")) {
            assertNull(bad, parseHexColor(bad))
        }
    }

    @Test fun formatsRgbOnly() {
        assertEquals("#1E5BD8", formatHexColor(0xFF1E5BD8))
        assertEquals("#00000A", formatHexColor(0x0A))
        assertEquals("#FFFFFF", formatHexColor(0xFFFFFFFF))
    }
}
