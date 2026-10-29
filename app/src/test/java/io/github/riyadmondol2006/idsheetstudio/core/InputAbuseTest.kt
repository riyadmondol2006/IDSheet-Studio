package io.github.riyadmondol2006.idsheetstudio.core

import io.github.riyadmondol2006.idsheetstudio.data.update.SemanticVersion
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import io.github.riyadmondol2006.idsheetstudio.image.CropMath
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfInspector
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

/** Garbage a user (or a corrupted file) can feed into text fields and parsers. */
class InputAbuseTest {
    @Test fun numberFieldsRejectNonsense() {
        for (bad in listOf("", " ", "-", ".", ",", "abc", "1..2", "1,2,3", "NaN", "Infinity", "-Infinity", "١٢", "12mm", "0x10", "∞")) {
            assertNull("'$bad' must be rejected", parseDecimal(bad))
        }
        assertEquals(1000.0, parseDecimal("1e3")!!, 0.0)
        assertEquals(-0.5, parseDecimal("-0,5")!!, 0.0)
        assertEquals(35.0, parseDecimal("  35  ")!!, 0.0)
    }

    @Test fun formattingNeverProducesExponentsOrCommas() {
        val r = Random(1)
        repeat(2000) {
            val v = r.nextDouble(-5000.0, 5000.0)
            val s = formatDecimal(v, 3)
            assertTrue(s, s.matches(Regex("-?\\d+(\\.\\d{1,3})?")))
            assertEquals(v, s.toDouble(), 0.0005)
        }
        assertEquals("0", formatDecimal(1e-12, 4))
        assertEquals("123456789", formatDecimal(123456789.0, 2))
    }

    @Test fun unitRoundTripsAreStable() {
        for (unit in LengthUnit.entries) for (mm in listOf(0.1, 1.0, 35.0, 50.8, 210.0, 355.6)) {
            val shown = formatDecimal(unit.fromMm(mm), unit.decimals)
            val back = unit.toMm(shown.toDouble())
            // Display precision: 0.1 mm, 0.01 cm (0.1 mm) and 0.001 in (0.0254 mm).
            assertEquals("$unit $mm", mm, back, 0.051)
        }
    }

    @Test fun gesturesWithDegenerateInputsKeepTheTransform() {
        val t = PhotoTransform(zoom = 1.5, offsetX = 0.1, offsetY = -0.1, rotation = 10.0)
        assertEquals(t, CropMath.applyGesture(t, 0.0, 100.0, 0.0, 0.0, 1.0, 1.0, 1.0, 0.0))
        assertEquals(t, CropMath.applyGesture(t, 100.0, 100.0, 0.0, 0.0, Double.NaN, 1.0, 1.0, 0.0))
        assertEquals(t, CropMath.applyGesture(t, 100.0, 100.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertEquals(t, CropMath.applyGesture(t, 100.0, 100.0, 0.0, 0.0, 0.0, 0.0, Double.POSITIVE_INFINITY, 0.0))
        assertTrue(CropMath.matrix(0.0, 0.0, 100.0, 100.0, t).all { it.isFinite() })
    }

    @Test fun frantickPinchingStaysBounded() {
        val r = Random(9)
        var t = PhotoTransform()
        repeat(10_000) {
            t = CropMath.applyGesture(
                t, 350.0, 450.0, r.nextDouble(0.0, 350.0), r.nextDouble(0.0, 450.0),
                r.nextDouble(-200.0, 200.0), r.nextDouble(-200.0, 200.0), r.nextDouble(0.5, 2.0), r.nextDouble(-30.0, 30.0),
            )
            assertTrue(t.zoom in PhotoTransform.MIN_ZOOM..PhotoTransform.MAX_ZOOM)
            assertTrue(t.rotation > -180.0 && t.rotation <= 180.0)
            assertTrue(t.offsetX.isFinite() && t.offsetY.isFinite())
        }
    }

    @Test fun weirdReleaseTags() {
        assertEquals("1.2.3", SemanticVersion.parse("v1.2.3+build.7").toString())
        assertEquals("1.0.0", SemanticVersion.parse("1").toString())
        assertEquals("1.2.3", SemanticVersion.parse("v01.02.03").toString())
        for (bad in listOf("", "v", "latest", "v1.2.3.4", "release-1", "1.2.x", "v-1.0.0")) assertNull(bad, SemanticVersion.parse(bad))
    }

    @Test fun pdfInspectorRejectsGarbageCleanly() {
        val r = Random(3)
        val samples = mutableListOf(ByteArray(0), "%PDF-1.4\n%%EOF".toByteArray(), "hello".toByteArray())
        repeat(200) { samples += ByteArray(r.nextInt(1, 4096)).also(r::nextBytes) }
        // Truncated and bit-flipped versions of a real document.
        val real = java.io.ByteArrayOutputStream().also { out -> PdfWriter(out).use { w -> repeat(3) { w.addPage(100.0, 100.0) {} } } }.toByteArray()
        for (cut in listOf(10, real.size / 3, real.size / 2, real.size - 30)) samples += real.copyOf(cut)
        repeat(50) { samples += real.copyOf().also { b -> b[r.nextInt(b.size)] = r.nextInt(256).toByte() } }
        for (bytes in samples) {
            try {
                PdfInspector.inspect(bytes)
            } catch (_: PdfInspector.InvalidPdfException) {
                // expected
            } catch (e: Throwable) {
                fail("Unexpected ${e.javaClass.simpleName} for input of ${bytes.size} bytes")
            }
        }
    }

    @Test fun largeDocumentsKeepAValidCrossReference() {
        val out = java.io.ByteArrayOutputStream()
        PdfWriter(out).use { w ->
            val img = w.addJpegImage(10, 10, byteArrayOf(1, 2, 3))
            repeat(250) { w.addPage(595.2756, 841.8898) { drawImage(img, 10.0, 10.0, 99.2126, 127.559) } }
        }
        val report = PdfInspector.inspect(out.toByteArray())
        assertTrue(report.xrefValid)
        assertEquals(250, report.pages.size)
        assertEquals(1, report.imageCount)
        assertTrue(report.pages.all { it.images.size == 1 && kotlin.math.abs(it.images[0].widthMm - 35.0) < 0.001 })
    }

    @Test fun titlesWithSpecialCharactersAreEscaped() {
        for (title in listOf("a)b(c", "back\\slash", "Ünïcødé 🎉", "", "(((", "line\nbreak")) {
            val out = java.io.ByteArrayOutputStream()
            PdfWriter(out, io.github.riyadmondol2006.idsheetstudio.pdf.PdfInfo(title = title)).use { it.addPage(10.0, 10.0) {} }
            assertTrue(PdfInspector.inspect(out.toByteArray()).xrefValid)
        }
    }
}
