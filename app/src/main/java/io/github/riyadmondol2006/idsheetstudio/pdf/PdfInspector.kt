package io.github.riyadmondol2006.idsheetstudio.pdf

import io.github.riyadmondol2006.idsheetstudio.core.pointsToMm
import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/**
 * Reads back PDFs produced by [PdfWriter] and reports their physical geometry. Used after every
 * export to prove the file has the intended page size, and by the test-suite to verify that
 * every photo is drawn at its exact physical dimensions.
 */
object PdfInspector {

    data class ImageDraw(val name: String, val xPt: Double, val yPt: Double, val widthPt: Double, val heightPt: Double, val rotated: Boolean) {
        val widthMm: Double get() = pointsToMm(widthPt)
        val heightMm: Double get() = pointsToMm(heightPt)
    }

    data class PageInfo(val widthPt: Double, val heightPt: Double, val images: List<ImageDraw>, val content: String) {
        val widthMm: Double get() = pointsToMm(widthPt)
        val heightMm: Double get() = pointsToMm(heightPt)
    }

    data class Report(val pages: List<PageInfo>, val objectCount: Int, val imageCount: Int, val xrefValid: Boolean)

    class InvalidPdfException(message: String) : Exception(message)

    private val NUM = """-?\d+(?:\.\d+)?"""
    private val MEDIA_BOX = Regex("""/MediaBox \[0 0 ($NUM) ($NUM)]""")
    private val CONTENTS = Regex("""/Contents (\d+) 0 R""")
    private val DRAW = Regex("""q ($NUM) ($NUM) ($NUM) ($NUM) ($NUM) ($NUM) cm /(Im\d+) Do Q""")

    fun inspect(bytes: ByteArray): Report = try {
        inspectUnchecked(bytes)
    } catch (e: InvalidPdfException) {
        throw e
    } catch (e: Exception) {
        // Includes checked exceptions such as DataFormatException from corrupted streams.
        throw InvalidPdfException("Malformed PDF: ${e.javaClass.simpleName}")
    }

    private fun inspectUnchecked(bytes: ByteArray): Report {
        val text = String(bytes, Charsets.ISO_8859_1)
        if (!text.startsWith("%PDF-")) throw InvalidPdfException("Missing PDF header")
        if (!text.trimEnd().endsWith("%%EOF")) throw InvalidPdfException("Missing %%EOF")

        val startXref = Regex("""startxref\s+(\d+)\s+%%EOF\s*$""").find(text)?.groupValues?.get(1)?.toInt()
            ?: throw InvalidPdfException("Missing startxref")
        val xrefHeader = Regex("""xref\s+0 (\d+)\s+""").find(text, startXref) ?: throw InvalidPdfException("Bad xref")
        if (xrefHeader.range.first != startXref) throw InvalidPdfException("startxref does not point at xref")
        val size = xrefHeader.groupValues[1].toInt()
        val entriesStart = xrefHeader.range.last + 1
        val offsets = HashMap<Int, Int>()
        var xrefValid = true
        for (id in 0 until size) {
            val entry = text.substring(entriesStart + id * 20, entriesStart + id * 20 + 20)
            if (id == 0) continue
            val offset = entry.substring(0, 10).toInt()
            offsets[id] = offset
            if (!text.startsWith("$id 0 obj", offset)) xrefValid = false
        }

        fun objectBody(id: Int): String {
            val start = offsets[id] ?: throw InvalidPdfException("Object $id missing")
            val end = text.indexOf("endobj", start)
            return text.substring(start, end)
        }

        fun streamBytes(id: Int): ByteArray {
            val start = offsets[id] ?: throw InvalidPdfException("Object $id missing")
            val dictEnd = text.indexOf("stream\n", start)
            val length = Regex("""/Length (\d+)""").find(text.substring(start, dictEnd))?.groupValues?.get(1)?.toInt()
                ?: throw InvalidPdfException("Stream $id has no length")
            val dataStart = dictEnd + "stream\n".length
            val data = bytes.copyOfRange(dataStart, dataStart + length)
            if (!text.startsWith("\nendstream", dataStart + length)) throw InvalidPdfException("Stream $id length mismatch")
            return if (text.substring(start, dictEnd).contains("/FlateDecode")) inflate(data) else data
        }

        val pages = mutableListOf<PageInfo>()
        var images = 0
        for (id in 1 until size) {
            val body = objectBody(id)
            when {
                body.contains("/Type /Page ") -> {
                    val box = MEDIA_BOX.find(body) ?: throw InvalidPdfException("Page $id has no MediaBox")
                    val contentId = CONTENTS.find(body)?.groupValues?.get(1)?.toInt() ?: throw InvalidPdfException("Page $id has no content")
                    val content = String(streamBytes(contentId), Charsets.ISO_8859_1)
                    val draws = DRAW.findAll(content).map { m ->
                        val v = m.groupValues.drop(1).take(6).map(String::toDouble)
                        val rotated = v[0] == 0.0 && v[3] == 0.0
                        if (rotated) {
                            // cm = [0 -h w 0 x y+h]
                            ImageDraw(m.groupValues[7], v[4], v[5] + v[1], v[2], -v[1], true)
                        } else {
                            ImageDraw(m.groupValues[7], v[4], v[5], v[0], v[3], false)
                        }
                    }.toList()
                    pages += PageInfo(box.groupValues[1].toDouble(), box.groupValues[2].toDouble(), draws, content)
                }
                body.contains("/Subtype /Image") -> images++
            }
        }
        // Pages appear in the page tree order, which matches object order for PdfWriter output.
        return Report(pages, size - 1, images, xrefValid)
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater()
        inflater.setInput(data)
        val out = ByteArrayOutputStream(data.size * 4)
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buffer)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buffer, 0, n)
        }
        inflater.end()
        return out.toByteArray()
    }
}
