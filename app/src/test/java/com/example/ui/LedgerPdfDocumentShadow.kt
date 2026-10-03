package com.example.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Resetter
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import java.io.OutputStream

/**
 * Robolectric has no PdfDocument native writer (its native handle is zero).
 * This test-only platform adapter exercises the unchanged exporter, file and share routing.
 * It deliberately does NOT claim to validate actual PDF serialization.
 */
@Implements(PdfDocument::class)
class LedgerPdfDocumentShadow {
    @Implementation fun __constructor__() = Unit
    @Implementation fun startPage(info: PdfDocument.PageInfo): PdfDocument.Page {
        startedPages++
        return ReflectionHelpers.callConstructor(PdfDocument.Page::class.java,
            ClassParameter.from(Canvas::class.java, Canvas(Bitmap.createBitmap(info.pageWidth, info.pageHeight, Bitmap.Config.ARGB_8888))),
            ClassParameter.from(PdfDocument.PageInfo::class.java, info))
    }
    @Implementation fun finishPage(page: PdfDocument.Page) { finishedPages++ }
    @Implementation fun writeTo(output: OutputStream) {
        writes++
        output.write("Ledger PDF platform test adapter".toByteArray())
    }
    @Implementation fun close() = Unit

    companion object {
        var startedPages = 0
        var finishedPages = 0
        var writes = 0
        @JvmStatic @Resetter fun reset() { startedPages = 0; finishedPages = 0; writes = 0 }
    }
}
