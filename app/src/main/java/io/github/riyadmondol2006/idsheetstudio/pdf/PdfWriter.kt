package io.github.riyadmondol2006.idsheetstudio.pdf

import io.github.riyadmondol2006.idsheetstudio.core.formatDecimal
import java.io.Closeable
import java.io.FilterOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater

/**
 * A small, dependency-free PDF 1.4 writer.
 *
 * Android's `PdfDocument` only accepts integer page sizes in points, which would make an A4 page
 * 595 × 842 pt instead of 595.276 × 841.890 pt (a 0.1 mm error). This writer emits exact
 * fractional page boxes, embeds JPEG images once and lets every page reference them, and streams
 * its output so memory use stays flat for large documents.
 */
class PdfWriter(
    output: OutputStream,
    private val info: PdfInfo = PdfInfo(),
    private val compressContent: Boolean = true,
) : Closeable {

    private val out = CountingOutputStream(output)
    private val offsets = HashMap<Int, Long>()
    private val pageIds = mutableListOf<Int>()
    private var nextId = FIRST_FREE_ID
    private var finished = false

    init {
        writeAscii("%PDF-1.4\n")
        // Binary marker so transfer tools treat the file as binary.
        out.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
    }

    class ImageRef internal constructor(internal val id: Int) {
        val name: String get() = "Im$id"
    }

    /** Embeds a baseline JPEG (DCTDecode, RGB) and returns a handle usable on any page. */
    fun addJpegImage(widthPx: Int, heightPx: Int, jpeg: ByteArray): ImageRef {
        check(!finished)
        require(widthPx > 0 && heightPx > 0)
        val id = nextId++
        beginObject(id)
        writeAscii(
            "<< /Type /XObject /Subtype /Image /Width $widthPx /Height $heightPx " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.size} >>\nstream\n",
        )
        out.write(jpeg)
        writeAscii("\nendstream\nendobj\n")
        return ImageRef(id)
    }

    /**
     * Adds a page of exactly [widthPt] × [heightPt] points. Content coordinates use the PDF
     * convention (origin bottom-left, 1 unit = 1/72 inch).
     */
    fun addPage(widthPt: Double, heightPt: Double, build: PdfContent.() -> Unit) {
        check(!finished)
        val content = PdfContent().apply(build)
        val raw = content.bytes()
        val contentId = nextId++
        beginObject(contentId)
        if (compressContent) {
            val compressed = deflate(raw)
            writeAscii("<< /Length ${compressed.size} /Filter /FlateDecode >>\nstream\n")
            out.write(compressed)
        } else {
            writeAscii("<< /Length ${raw.size} >>\nstream\n")
            out.write(raw)
        }
        writeAscii("\nendstream\nendobj\n")

        val pageId = nextId++
        val box = "[0 0 ${num(widthPt)} ${num(heightPt)}]"
        val xObjects = content.images.joinToString(" ") { "/${it.name} ${it.id} 0 R" }
        beginObject(pageId)
        writeAscii(
            "<< /Type /Page /Parent $PAGES_ID 0 R /MediaBox $box /CropBox $box /TrimBox $box " +
                "/Resources << /ProcSet [/PDF /ImageC] /XObject << $xObjects >> >> " +
                "/Contents $contentId 0 R >>\nendobj\n",
        )
        pageIds += pageId
    }

    val pageCount: Int get() = pageIds.size

    /** Writes the page tree, document info, cross-reference table and trailer. */
    fun finish() {
        if (finished) return
        finished = true
        beginObject(PAGES_ID)
        writeAscii("<< /Type /Pages /Count ${pageIds.size} /Kids [${pageIds.joinToString(" ") { "$it 0 R" }}] >>\nendobj\n")

        beginObject(CATALOG_ID)
        writeAscii("<< /Type /Catalog /Pages $PAGES_ID 0 R /ViewerPreferences << /PrintScaling /None >> >>\nendobj\n")

        beginObject(INFO_ID)
        val date = pdfDate(info.creationTime)
        writeAscii(
            "<< /Title ${pdfString(info.title)} /Producer ${pdfString(info.producer)} " +
                "/Creator ${pdfString(info.creator)} /CreationDate ${pdfString(date)} /ModDate ${pdfString(date)} >>\nendobj\n",
        )

        val xrefOffset = out.count
        val size = nextId
        val sb = StringBuilder()
        sb.append("xref\n0 ").append(size).append('\n')
        sb.append("0000000000 65535 f \n")
        for (id in 1 until size) {
            val offset = offsets[id] ?: error("Object $id was never written")
            sb.append(offset.toString().padStart(10, '0')).append(" 00000 n \n")
        }
        sb.append("trailer\n<< /Size ").append(size)
            .append(" /Root ").append(CATALOG_ID).append(" 0 R /Info ").append(INFO_ID).append(" 0 R >>\n")
        sb.append("startxref\n").append(xrefOffset).append("\n%%EOF\n")
        writeAscii(sb.toString())
        out.flush()
    }

    override fun close() {
        finish()
        out.close()
    }

    private fun beginObject(id: Int) {
        check(id !in offsets) { "Object $id written twice" }
        offsets[id] = out.count
        writeAscii("$id 0 obj\n")
    }

    private fun writeAscii(text: String) = out.write(text.toByteArray(Charsets.ISO_8859_1))

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(data)
        deflater.finish()
        val buffer = ByteArray(8192)
        val result = java.io.ByteArrayOutputStream(data.size / 2 + 64)
        while (!deflater.finished()) {
            val n = deflater.deflate(buffer)
            result.write(buffer, 0, n)
        }
        deflater.end()
        return result.toByteArray()
    }

    private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
        var count = 0L
            private set

        override fun write(b: Int) {
            out.write(b)
            count++
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            out.write(b, off, len)
            count += len
        }
    }

    companion object {
        private const val CATALOG_ID = 1
        private const val PAGES_ID = 2
        private const val INFO_ID = 3
        private const val FIRST_FREE_ID = 4

        fun num(value: Double): String = formatDecimal(value, 4)

        fun pdfDate(millis: Long): String {
            val format = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            return "D:${format.format(Date(millis))}Z"
        }

        /** Encodes text as a PDF string: escaped literal for ASCII, UTF-16BE hex otherwise. */
        fun pdfString(text: String): String {
            if (text.all { it.code in 32..126 }) {
                val escaped = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")
                return "($escaped)"
            }
            val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)
            return "<" + bytes.joinToString("") { "%02X".format(it.toInt() and 0xFF) } + ">"
        }
    }
}

data class PdfInfo(
    val title: String = "IDSheet Studio sheet",
    val producer: String = "IDSheet Studio",
    val creator: String = "IDSheet Studio",
    val creationTime: Long = System.currentTimeMillis(),
)

/** Builder for a page content stream. All coordinates are in points, origin bottom-left. */
class PdfContent internal constructor() {
    private val sb = StringBuilder()
    internal val images = LinkedHashSet<PdfWriter.ImageRef>()

    private fun n(v: Double) = PdfWriter.num(v)

    internal fun bytes(): ByteArray = sb.toString().toByteArray(Charsets.ISO_8859_1)

    /** Draws [image] into the rectangle (x, y, w, h). [rotateClockwise] turns it 90° to fill it. */
    fun drawImage(image: PdfWriter.ImageRef, x: Double, y: Double, w: Double, h: Double, rotateClockwise: Boolean = false) {
        images += image
        if (rotateClockwise) {
            // Maps the image's unit square so its top edge lands on the rectangle's right edge.
            sb.append("q 0 ${n(-h)} ${n(w)} 0 ${n(x)} ${n(y + h)} cm /${image.name} Do Q\n")
        } else {
            sb.append("q ${n(w)} 0 0 ${n(h)} ${n(x)} ${n(y)} cm /${image.name} Do Q\n")
        }
    }

    fun setStroke(widthPt: Double, argb: Long, dashPt: DoubleArray?, roundCap: Boolean) {
        val r = ((argb shr 16) and 0xFF) / 255.0
        val g = ((argb shr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        sb.append("${n(r)} ${n(g)} ${n(b)} RG ${n(widthPt)} w ${if (roundCap) 1 else 0} J 0 j ")
        if (dashPt == null) sb.append("[] 0 d\n") else sb.append("[${dashPt.joinToString(" ") { n(it) }}] 0 d\n")
    }

    fun strokeRect(x: Double, y: Double, w: Double, h: Double) {
        sb.append("${n(x)} ${n(y)} ${n(w)} ${n(h)} re S\n")
    }

    fun strokeLine(x1: Double, y1: Double, x2: Double, y2: Double) {
        sb.append("${n(x1)} ${n(y1)} m ${n(x2)} ${n(y2)} l S\n")
    }

    fun saveState() { sb.append("q\n") }
    fun restoreState() { sb.append("Q\n") }
}
