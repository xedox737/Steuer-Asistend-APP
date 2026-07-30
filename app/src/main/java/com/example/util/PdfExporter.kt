package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    private const val TAG = "PdfExporter"

    // Page layout constants for A4 (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT // 515f

    /**
     * Generates a tax office compatible, structured PDF report from receipts of a specific tax year.
     * Saves the PDF to cache and returns the shared file URI.
     */
    fun exportReceiptsToPdf(
        context: Context,
        taxYear: Int,
        receipts: List<Receipt>,
        metadata: PropertyMetadata?,
        includeTaxAdvisorSummary: Boolean = false
    ): File? {
        // Filter receipts strictly by tax year
        val filteredReceipts = receipts.filter {
            try {
                it.datum.substring(0, 4).toInt() == taxYear
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.datum } // Chronological order

        val document = PdfDocument()
        var pageNumber = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var yPos = 60f

        // Text formatting helpers
        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        // Common Paints
        val textPaintNormal = Paint().apply {
            color = 0xFF1E293B.toInt() // Dark slate slate-800
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textPaintBold = Paint().apply {
            color = 0xFF0F172A.toInt() // Deep slate slate-900
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        // Running Header & Footer
        fun drawPageDecorations(cv: Canvas, pNum: Int) {
            val linePaint = Paint().apply {
                color = 0xFFE2E8F0.toInt() // Slate-200
                strokeWidth = 0.75f
            }
            val decPaint = Paint().apply {
                color = 0xFF64748B.toInt() // Slate-500
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            // Running header
            cv.drawText("Steuerlicher Belegbericht (Finanzamt-Export) - Steuerjahr $taxYear", MARGIN_LEFT, 30f, decPaint)
            cv.drawLine(MARGIN_LEFT, 35f, PAGE_WIDTH - MARGIN_RIGHT, 35f, linePaint)

            // Running footer
            cv.drawLine(MARGIN_LEFT, PAGE_HEIGHT - 45f, PAGE_WIDTH - MARGIN_RIGHT, PAGE_HEIGHT - 45f, linePaint)
            val formatStr = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date())
            cv.drawText("Erstellt am: $formatStr", MARGIN_LEFT, PAGE_HEIGHT - 32f, decPaint)
            
            val pText = "Seite $pNum"
            val textWidth = decPaint.measureText(pText)
            cv.drawText(pText, PAGE_WIDTH - MARGIN_RIGHT - textWidth, PAGE_HEIGHT - 32f, decPaint)
        }

        fun startNewPage() {
            currentPage?.let { document.finishPage(it) }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            currentPage = document.startPage(pageInfo)
            canvas = currentPage!!.canvas
            drawPageDecorations(canvas!!, pageNumber)
            yPos = 55f // Reset drawing offset on new page
        }

        fun checkSpaceAndPageBreak(requiredHeight: Float) {
            if (yPos + requiredHeight > PAGE_HEIGHT - 60f) {
                startNewPage()
            }
        }

        fun Paint.truncate(text: String, maxWidth: Float): String {
            if (measureText(text) <= maxWidth) return text
            var len = text.length
            while (len > 0 && measureText(text.substring(0, len) + "...") > maxWidth) {
                len--
            }
            return if (len > 0) text.substring(0, len) + "..." else "..."
        }

        // Begin Page 1
        startNewPage()
        val cv = canvas!!

        // Report Primary Header
        val titlePaint = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = 0xFF10B981.toInt() // Emerald theme accent
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        cv.drawText("Steuerlicher Beleg- & Buchungsbericht", MARGIN_LEFT, yPos + 15f, titlePaint)
        cv.drawText("Steuerjahr: $taxYear (Konform für Anlage V / Sonderausgaben)", MARGIN_LEFT, yPos + 32f, subtitlePaint)
        yPos += 45f

        // Property Metadata Box (Rented / Real Estate Context)
        if (metadata != null) {
            checkSpaceAndPageBreak(85f)
            val cardBg = Paint().apply { color = 0xFFF8FAFC.toInt(); style = Paint.Style.FILL }
            val cardBorder = Paint().apply { color = 0xFFE2E8F0.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
            
            val rect = RectF(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 75f)
            cv.drawRoundRect(rect, 6f, 6f, cardBg)
            cv.drawRoundRect(rect, 6f, 6f, cardBorder)

            val metaBold = Paint().apply {
                color = 0xFF334155.toInt() // Slate-700
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val metaNormal = Paint().apply {
                color = 0xFF475569.toInt() // Slate-600
                textSize = 9f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            cv.drawText("STEUEROBJEKT & IMMOBILIEN-METADATEN", MARGIN_LEFT + 15f, yPos + 18f, metaBold)
            cv.drawText("Bezeichnung: ${metadata.name}", MARGIN_LEFT + 15f, yPos + 34f, metaNormal)
            cv.drawText("Adresse:     ${metadata.adresse}", MARGIN_LEFT + 15f, yPos + 48f, metaNormal)
            cv.drawText("Einheiten:   ${metadata.wohneinheiten}", MARGIN_LEFT + 15f, yPos + 62f, metaNormal)
            
            yPos += 90f
        }

        // Summary Statistics (KPI Cards)
        checkSpaceAndPageBreak(65f)
        val kpiBg = Paint().apply { color = 0xFFF1F5F9.toInt(); style = Paint.Style.FILL }
        val kpiBorder = Paint().apply { color = 0xFFCBD5E1.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
        
        val kpiWidth = (CONTENT_WIDTH - 20f) / 3f
        val kpiHeight = 50f

        val kpiLabels = listOf("Anzahl Belege", "Summe Bruttobetrag", "Eigenleistungen")
        val totalSum = filteredReceipts.sumOf { it.bruttobetrag }
        val eigenSum = filteredReceipts.filter { it.isEigenleistungSanierung }.sumOf { it.bruttobetrag }
        val kpiValues = listOf(
            "${filteredReceipts.size}",
            currencyFormatter.format(totalSum),
            currencyFormatter.format(eigenSum)
        )

        for (i in 0..2) {
            val left = MARGIN_LEFT + i * (kpiWidth + 10f)
            val right = left + kpiWidth
            val rect = RectF(left, yPos, right, yPos + kpiHeight)
            cv.drawRoundRect(rect, 4f, 4f, kpiBg)
            cv.drawRoundRect(rect, 4f, 4f, kpiBorder)

            val labelPaint = Paint().apply {
                color = 0xFF64748B.toInt()
                textSize = 7.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }
            val valuePaint = Paint().apply {
                color = if (i == 1) 0xFF10B981.toInt() else 0xFF0F172A.toInt()
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            val lWidth = labelPaint.measureText(kpiLabels[i])
            cv.drawText(kpiLabels[i], left + (kpiWidth - lWidth) / 2f, yPos + 18f, labelPaint)
            val vWidth = valuePaint.measureText(kpiValues[i])
            cv.drawText(kpiValues[i], left + (kpiWidth - vWidth) / 2f, yPos + 38f, valuePaint)
        }
        yPos += 65f

        // Section: Category Breakdown
        checkSpaceAndPageBreak(40f)
        val sectionTitlePaint = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        cv.drawText("Zusammenfassung nach Werbungskosten-Kategorien (Anlage V)", MARGIN_LEFT, yPos + 10f, sectionTitlePaint)
        yPos += 18f

        val categoryGroups = filteredReceipts.groupBy { it.hauptkategorie }
            .mapValues { (_, list) ->
                val sum = list.sumOf { it.bruttobetrag }
                val count = list.size
                Pair(count, sum)
            }.toList().sortedByDescending { it.second.second }

        // Category Table Header
        checkSpaceAndPageBreak(30f)
        val tableHeaderBg = Paint().apply { color = 0xFF334155.toInt(); style = Paint.Style.FILL }
        val tableHeaderPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, tableHeaderBg)
        cv.drawText("Kategorie", MARGIN_LEFT + 8f, yPos + 12f, tableHeaderPaint)
        cv.drawText("Anzahl Belege", MARGIN_LEFT + 280f, yPos + 12f, tableHeaderPaint)
        
        val totalHeaderWidth = tableHeaderPaint.measureText("Gesamtsumme (Brutto)")
        cv.drawText("Gesamtsumme (Brutto)", PAGE_WIDTH - MARGIN_RIGHT - 8f - totalHeaderWidth, yPos + 12f, tableHeaderPaint)
        yPos += 18f

        // Category Table Rows
        val rowBgEven = Paint().apply { color = 0xFFF8FAFC.toInt(); style = Paint.Style.FILL }
        val rowBgOdd = Paint().apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
        val rowBorderPaint = Paint().apply { color = 0xFFE2E8F0.toInt(); strokeWidth = 0.5f }

        categoryGroups.forEachIndexed { idx, (category, stats) ->
            checkSpaceAndPageBreak(16f)
            val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
            cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rBg)
            cv.drawLine(MARGIN_LEFT, yPos + 16f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rowBorderPaint)

            val catStr = textPaintBold.truncate(category, 250f)
            cv.drawText(catStr, MARGIN_LEFT + 8f, yPos + 11f, textPaintBold)
            cv.drawText("${stats.first} Belege", MARGIN_LEFT + 280f, yPos + 11f, textPaintNormal)
            
            val valStr = currencyFormatter.format(stats.second)
            val valWidth = textPaintBold.measureText(valStr)
            cv.drawText(valStr, PAGE_WIDTH - MARGIN_RIGHT - 8f - valWidth, yPos + 11f, textPaintBold)
            yPos += 16f
        }
        yPos += 15f

        // Section: Steuerberater-Zusammenfassung (DATEV-Kontenrahmen)
        if (includeTaxAdvisorSummary) {
            checkSpaceAndPageBreak(50f)
            val advisorTitlePaint = Paint().apply {
                color = 0xFF0284C7.toInt() // Distinct sky-blue 600 theme color for Steuerberater info
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            cv.drawText("DATEV-Kontenrahmen-Zusammenfassung (für Steuerberater)", MARGIN_LEFT, yPos + 10f, advisorTitlePaint)
            yPos += 18f

            // Table Header
            checkSpaceAndPageBreak(30f)
            val stBHeaderBg = Paint().apply { color = 0xFF0284C7.toInt(); style = Paint.Style.FILL }
            val stBHeaderPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, stBHeaderBg)
            cv.drawText("Soll-Kto", MARGIN_LEFT + 6f, yPos + 12f, stBHeaderPaint)
            cv.drawText("Haben-Kto", MARGIN_LEFT + 60f, yPos + 12f, stBHeaderPaint)
            cv.drawText("Bezeichnung / Werbungskosten-Kategorie", MARGIN_LEFT + 130f, yPos + 12f, stBHeaderPaint)
            cv.drawText("Belege", MARGIN_LEFT + 340f, yPos + 12f, stBHeaderPaint)
            cv.drawText("USt %", MARGIN_LEFT + 390f, yPos + 12f, stBHeaderPaint)
            
            val totalStBAmtStr = "Summe (€)"
            val totalStBAmtWidth = stBHeaderPaint.measureText(totalStBAmtStr)
            cv.drawText(totalStBAmtStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - totalStBAmtWidth, yPos + 12f, stBHeaderPaint)
            yPos += 18f

            // Group filteredReceipts by account number (kontoNr)
            val kontoGroups = filteredReceipts.groupBy { it.kontoNr }
                .mapValues { (konto, list) ->
                    val sum = list.sumOf { it.bruttobetrag }
                    val count = list.size
                    val categoryName = list.firstOrNull()?.hauptkategorie ?: "Sonstige Ausgaben"
                    Triple(count, sum, categoryName)
                }.toList().sortedBy { it.first }

            kontoGroups.forEachIndexed { idx, (konto, data) ->
                checkSpaceAndPageBreak(16f)
                val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
                cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rBg)
                cv.drawLine(MARGIN_LEFT, yPos + 16f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rowBorderPaint)

                val kontoStr = if (konto.isNotEmpty()) konto else "k.A."
                cv.drawText(kontoStr, MARGIN_LEFT + 6f, yPos + 11f, textPaintBold)
                
                // Standard offset bank account in German tax advising is often 1200 / 1800
                cv.drawText("1200 (Bank)", MARGIN_LEFT + 60f, yPos + 11f, textPaintNormal)
                
                val nameStr = textPaintNormal.truncate(data.third, 200f)
                cv.drawText(nameStr, MARGIN_LEFT + 130f, yPos + 11f, textPaintNormal)
                
                cv.drawText("${data.first}", MARGIN_LEFT + 340f, yPos + 11f, textPaintNormal)
                cv.drawText("19%", MARGIN_LEFT + 390f, yPos + 11f, textPaintNormal)

                val sumStr = currencyFormatter.format(data.second)
                val sumWidth = textPaintBold.measureText(sumStr)
                cv.drawText(sumStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - sumWidth, yPos + 11f, textPaintBold)
                yPos += 16f
            }
            yPos += 20f
        }

        // Section: Detailed Ledger
        checkSpaceAndPageBreak(40f)
        cv.drawText("Einzelaufstellung der erfassten Belege ($taxYear)", MARGIN_LEFT, yPos + 10f, sectionTitlePaint)
        yPos += 18f

        // Ledger Table Header
        checkSpaceAndPageBreak(30f)
        cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, tableHeaderBg)
        cv.drawText("Datum", MARGIN_LEFT + 6f, yPos + 12f, tableHeaderPaint)
        cv.drawText("Aussteller / Kreditor", MARGIN_LEFT + 70f, yPos + 12f, tableHeaderPaint)
        cv.drawText("Beschreibung / Unterkategorie", MARGIN_LEFT + 200f, yPos + 12f, tableHeaderPaint)
        cv.drawText("Konto", MARGIN_LEFT + 410f, yPos + 12f, tableHeaderPaint)
        
        val amtHeaderStr = "Betrag (€)"
        val amtHeaderWidth = tableHeaderPaint.measureText(amtHeaderStr)
        cv.drawText(amtHeaderStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - amtHeaderWidth, yPos + 12f, tableHeaderPaint)
        yPos += 18f

        // Ledger Table Rows
        filteredReceipts.forEachIndexed { idx, r ->
            checkSpaceAndPageBreak(18f)
            val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
            cv.drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, rBg)
            cv.drawLine(MARGIN_LEFT, yPos + 18f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, rowBorderPaint)

            // Format date nicely (DD.MM.YYYY)
            val dateFormatted = try {
                val inputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val outputFmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                inputFmt.parse(r.datum)?.let { outputFmt.format(it) } ?: r.datum
            } catch (e: Exception) {
                r.datum
            }

            cv.drawText(dateFormatted, MARGIN_LEFT + 6f, yPos + 12f, textPaintNormal)
            
            val ausstellerStr = textPaintBold.truncate(r.aussteller, 120f)
            cv.drawText(ausstellerStr, MARGIN_LEFT + 70f, yPos + 12f, textPaintBold)

            val descText = if (r.beschreibung.isNotEmpty()) r.beschreibung else r.unterkategorie
            val descStr = textPaintNormal.truncate(descText, 200f)
            cv.drawText(descStr, MARGIN_LEFT + 200f, yPos + 12f, textPaintNormal)

            val kontoStr = textPaintNormal.truncate(r.kontoNr, 45f)
            cv.drawText(kontoStr, MARGIN_LEFT + 410f, yPos + 12f, textPaintNormal)

            // Betrag bold with potential Green if Eigenleistung
            val amountStr = currencyFormatter.format(r.bruttobetrag)
            val amountWidth = textPaintBold.measureText(amountStr)
            
            val amountPaint = Paint(textPaintBold).apply {
                if (r.isEigenleistungSanierung) {
                    color = 0xFF059669.toInt() // Darker Emerald
                }
            }
            cv.drawText(amountStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - amountWidth, yPos + 12f, amountPaint)
            yPos += 18f
        }

        // Close Document
        currentPage?.let { document.finishPage(it) }

        // Save PDF to cache dir for sharing
        val cacheDir = context.cacheDir
        val filename = if (includeTaxAdvisorSummary) "Steuerbericht_Steuerberater_${taxYear}.pdf" else "Steuerbericht_Finanzamt_${taxYear}.pdf"
        val pdfFile = File(cacheDir, filename)
        
        return try {
            val fos = FileOutputStream(pdfFile)
            document.writeTo(fos)
            fos.close()
            document.close()
            val fileSizeKb = pdfFile.length() / 1024
            Log.d(TAG, "PDF successfully generated at: ${pdfFile.absolutePath} (Size: ${fileSizeKb} KB)")
            pdfFile
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write PDF", e)
            document.close()
            null
        }
    }

    /**
     * Triggers the Android Share Sheet to let the user export or print the PDF.
     */
    fun sharePdf(context: Context, file: File) {
        val authority = "${context.packageName}.provider"
        try {
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Steuerbericht exportieren"))
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing PDF: ${e.localizedMessage}", e)
        }
    }
}
