package com.example.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.OutputStream

/** Robolectric 4.16.1 has no native PdfDocument writer (native handle = 0).
 * Adapt only that platform boundary: retain the product's actual pages, text
 * and positions in a parseable PDF. Native Android PDF serialization remains
 * an emulator/device concern; this adapter is never packaged with the app.
 */
@Implements(PdfDocument::class)
class AdvisorPdfDocumentShadow {
    private data class Text(val value: String, val x: Float, val y: Float, val size: Float)
    private class TextCanvas : Canvas() {
        val text = mutableListOf<Text>()
        override fun drawText(value: String, x: Float, y: Float, paint: Paint) {
            text += Text(value, x, y, paint.textSize)
        }
    }
    private data class Page(val info: PdfDocument.PageInfo, val canvas: TextCanvas)
    private val finished = mutableListOf<Page>()
    private var current: Page? = null
    private var closed = false

    @Implementation fun __constructor__() = Unit
    @Implementation fun startPage(info: PdfDocument.PageInfo): PdfDocument.Page {
        check(!closed && current == null)
        val canvas = TextCanvas()
        current = Page(info, canvas)
        return ReflectionHelpers.callConstructor(PdfDocument.Page::class.java,
            ClassParameter.from(Canvas::class.java, canvas),
            ClassParameter.from(PdfDocument.PageInfo::class.java, info))
    }
    @Implementation fun finishPage(page: PdfDocument.Page) {
        check(!closed)
        val pending = requireNotNull(current)
        check(pending.info == page.info)
        finished += pending
        current = null
    }
    @Implementation fun writeTo(output: OutputStream) {
        check(!closed && current == null && finished.isNotEmpty())
        PDDocument().use { document ->
            finished.forEach { source ->
                val page = PDPage(PDRectangle(source.info.pageWidth.toFloat(), source.info.pageHeight.toFloat()))
                document.addPage(page)
                PDPageContentStream(document, page).use { stream ->
                    source.canvas.text.forEach { text ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, text.size)
                        stream.newLineAtOffset(text.x, source.info.pageHeight - text.y)
                        stream.showText(text.value)
                        stream.endText()
                    }
                }
            }
            document.save(output)
        }
    }
    @Implementation fun close() { check(current == null); closed = true }
}
