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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {
    private const val TAG = "PdfExporter"

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN_LEFT = 40f
    private const val MARGIN_RIGHT = 40f
    private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT

    fun exportReceiptsToPdf(
        context: Context,
        taxYear: Int,
        receipts: List<Receipt>,
        metadata: PropertyMetadata?,
        includeTaxAdvisorSummary: Boolean = false
    ): File? {
        val filteredReceipts = receipts.filter {
            try {
                it.datum.substring(0, 4).toInt() == taxYear
            } catch (e: Exception) {
                false
            }
        }.sortedBy { it.datum }

        val document = PdfDocument()
        var pageNumber = 0
        var currentPage: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var yPos = 60f

        val currencyFormatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)

        val textPaintNormal = Paint().apply {
            color = 0xFF1E293B.toInt()
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val textPaintBold = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        fun drawPageDecorations(cv: Canvas, pNum: Int) {
            val linePaint = Paint().apply {
                color = 0xFFE2E8F0.toInt()
                strokeWidth = 0.75f
            }
            val decPaint = Paint().apply {
                color = 0xFF64748B.toInt()
                textSize = 8f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                isAntiAlias = true
            }

            cv.drawText("Steuerlicher Belegbericht (Finanzamt-Export) - Steuerjahr $taxYear", MARGIN_LEFT, 30f, decPaint)
            cv.drawLine(MARGIN_LEFT, 35f, PAGE_WIDTH - MARGIN_RIGHT, 35f, linePaint)
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
            yPos = 55f
        }

        fun checkSpaceAndPageBreak(requiredHeight: Float) {
            if (yPos + requiredHeight > PAGE_HEIGHT - 60f) {
                startNewPage()
            }
        }

        fun activeCanvas(): Canvas = requireNotNull(canvas) { "PDF canvas is not available" }

        fun Paint.truncate(text: String, maxWidth: Float): String {
            if (measureText(text) <= maxWidth) return text
            var len = text.length
            while (len > 0 && measureText(text.substring(0, len) + "...") > maxWidth) {
                len--
            }
            return if (len > 0) text.substring(0, len) + "..." else "..."
        }

        return try {
            startNewPage()

            val titlePaint = Paint().apply {
                color = 0xFF0F172A.toInt()
                textSize = 18f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            val subtitlePaint = Paint().apply {
                color = 0xFF10B981.toInt()
                textSize = 12f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }

            activeCanvas().drawText("Steuerlicher Beleg- & Buchungsbericht", MARGIN_LEFT, yPos + 15f, titlePaint)
            activeCanvas().drawText("Steuerjahr: $taxYear (Konform für Anlage V / Sonderausgaben)", MARGIN_LEFT, yPos + 32f, subtitlePaint)
            yPos += 45f

            if (metadata != null) {
                checkSpaceAndPageBreak(85f)
                val cardBg = Paint().apply { color = 0xFFF8FAFC.toInt(); style = Paint.Style.FILL }
                val cardBorder = Paint().apply { color = 0xFFE2E8F0.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
                val rect = RectF(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 75f)
                activeCanvas().drawRoundRect(rect, 6f, 6f, cardBg)
                activeCanvas().drawRoundRect(rect, 6f, 6f, cardBorder)

                val metaBold = Paint().apply {
                    color = 0xFF334155.toInt()
                    textSize = 9f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                val metaNormal = Paint().apply {
                    color = 0xFF475569.toInt()
                    textSize = 9f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    isAntiAlias = true
                }

                activeCanvas().drawText("STEUEROBJEKT & IMMOBILIEN-METADATEN", MARGIN_LEFT + 15f, yPos + 18f, metaBold)
                activeCanvas().drawText("Bezeichnung: ${metadata.name}", MARGIN_LEFT + 15f, yPos + 34f, metaNormal)
                activeCanvas().drawText("Adresse:     ${metadata.adresse}", MARGIN_LEFT + 15f, yPos + 48f, metaNormal)
                activeCanvas().drawText("Einheiten:   ${metadata.wohneinheiten}", MARGIN_LEFT + 15f, yPos + 62f, metaNormal)
                yPos += 90f
            }

            checkSpaceAndPageBreak(65f)
            val kpiBg = Paint().apply { color = 0xFFF1F5F9.toInt(); style = Paint.Style.FILL }
            val kpiBorder = Paint().apply { color = 0xFFCBD5E1.toInt(); style = Paint.Style.STROKE; strokeWidth = 1f }
            val kpiWidth = (CONTENT_WIDTH - 20f) / 3f
            val kpiHeight = 50f
            val kpiLabels = listOf("Anzahl Belege", "Summe Bruttobetrag", "Eigenleistungen")
            val totalSum = filteredReceipts.sumOf { it.bruttobetrag }
            val eigenSum = filteredReceipts.filter { it.isEigenleistungSanierung }.sumOf { it.bruttobetrag }
            val kpiValues = listOf("${filteredReceipts.size}", currencyFormatter.format(totalSum), currencyFormatter.format(eigenSum))

            for (i in 0..2) {
                val left = MARGIN_LEFT + i * (kpiWidth + 10f)
                val right = left + kpiWidth
                val rect = RectF(left, yPos, right, yPos + kpiHeight)
                activeCanvas().drawRoundRect(rect, 4f, 4f, kpiBg)
                activeCanvas().drawRoundRect(rect, 4f, 4f, kpiBorder)

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
                activeCanvas().drawText(kpiLabels[i], left + (kpiWidth - lWidth) / 2f, yPos + 18f, labelPaint)
                val vWidth = valuePaint.measureText(kpiValues[i])
                activeCanvas().drawText(kpiValues[i], left + (kpiWidth - vWidth) / 2f, yPos + 38f, valuePaint)
            }
            yPos += 65f

            checkSpaceAndPageBreak(40f)
            val sectionTitlePaint = Paint().apply {
                color = 0xFF0F172A.toInt()
                textSize = 11f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            activeCanvas().drawText("Zusammenfassung nach Werbungskosten-Kategorien (Anlage V)", MARGIN_LEFT, yPos + 10f, sectionTitlePaint)
            yPos += 18f

            val categoryGroups = filteredReceipts.groupBy { it.hauptkategorie }
                .mapValues { (_, list) -> Pair(list.size, list.sumOf { it.bruttobetrag }) }
                .toList().sortedByDescending { it.second.second }

            checkSpaceAndPageBreak(30f)
            val tableHeaderBg = Paint().apply { color = 0xFF334155.toInt(); style = Paint.Style.FILL }
            val tableHeaderPaint = Paint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                isAntiAlias = true
            }
            activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, tableHeaderBg)
            activeCanvas().drawText("Kategorie", MARGIN_LEFT + 8f, yPos + 12f, tableHeaderPaint)
            activeCanvas().drawText("Anzahl Belege", MARGIN_LEFT + 280f, yPos + 12f, tableHeaderPaint)
            val totalHeaderWidth = tableHeaderPaint.measureText("Gesamtsumme (Brutto)")
            activeCanvas().drawText("Gesamtsumme (Brutto)", PAGE_WIDTH - MARGIN_RIGHT - 8f - totalHeaderWidth, yPos + 12f, tableHeaderPaint)
            yPos += 18f

            val rowBgEven = Paint().apply { color = 0xFFF8FAFC.toInt(); style = Paint.Style.FILL }
            val rowBgOdd = Paint().apply { color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL }
            val rowBorderPaint = Paint().apply { color = 0xFFE2E8F0.toInt(); strokeWidth = 0.5f }

            categoryGroups.forEachIndexed { idx, (category, stats) ->
                checkSpaceAndPageBreak(16f)
                val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
                activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rBg)
                activeCanvas().drawLine(MARGIN_LEFT, yPos + 16f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rowBorderPaint)
                val catStr = textPaintBold.truncate(category, 250f)
                activeCanvas().drawText(catStr, MARGIN_LEFT + 8f, yPos + 11f, textPaintBold)
                activeCanvas().drawText("${stats.first} Belege", MARGIN_LEFT + 280f, yPos + 11f, textPaintNormal)
                val valStr = currencyFormatter.format(stats.second)
                val valWidth = textPaintBold.measureText(valStr)
                activeCanvas().drawText(valStr, PAGE_WIDTH - MARGIN_RIGHT - 8f - valWidth, yPos + 11f, textPaintBold)
                yPos += 16f
            }
            yPos += 15f

            if (includeTaxAdvisorSummary) {
                checkSpaceAndPageBreak(50f)
                val advisorTitlePaint = Paint().apply {
                    color = 0xFF0284C7.toInt()
                    textSize = 11f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                activeCanvas().drawText("DATEV-Kontenrahmen-Zusammenfassung (für Steuerberater)", MARGIN_LEFT, yPos + 10f, advisorTitlePaint)
                yPos += 18f

                checkSpaceAndPageBreak(30f)
                val stBHeaderBg = Paint().apply { color = 0xFF0284C7.toInt(); style = Paint.Style.FILL }
                val stBHeaderPaint = Paint().apply {
                    color = 0xFFFFFFFF.toInt()
                    textSize = 8.5f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    isAntiAlias = true
                }
                activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, stBHeaderBg)
                activeCanvas().drawText("Soll-Kto", MARGIN_LEFT + 6f, yPos + 12f, stBHeaderPaint)
                activeCanvas().drawText("Haben-Kto", MARGIN_LEFT + 60f, yPos + 12f, stBHeaderPaint)
                activeCanvas().drawText("Bezeichnung / Werbungskosten-Kategorie", MARGIN_LEFT + 130f, yPos + 12f, stBHeaderPaint)
                activeCanvas().drawText("Belege", MARGIN_LEFT + 340f, yPos + 12f, stBHeaderPaint)
                activeCanvas().drawText("USt %", MARGIN_LEFT + 390f, yPos + 12f, stBHeaderPaint)
                val totalStBAmtStr = "Summe (€)"
                val totalStBAmtWidth = stBHeaderPaint.measureText(totalStBAmtStr)
                activeCanvas().drawText(totalStBAmtStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - totalStBAmtWidth, yPos + 12f, stBHeaderPaint)
                yPos += 18f

                val kontoGroups = filteredReceipts.groupBy { it.kontoNr }
                    .mapValues { (_, list) -> Triple(list.size, list.sumOf { it.bruttobetrag }, list.firstOrNull()?.hauptkategorie ?: "Sonstige Ausgaben") }
                    .toList().sortedBy { it.first }

                kontoGroups.forEachIndexed { idx, (konto, data) ->
                    checkSpaceAndPageBreak(16f)
                    val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
                    activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rBg)
                    activeCanvas().drawLine(MARGIN_LEFT, yPos + 16f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 16f, rowBorderPaint)
                    val kontoStr = if (konto.isNotEmpty()) konto else "k.A."
                    activeCanvas().drawText(kontoStr, MARGIN_LEFT + 6f, yPos + 11f, textPaintBold)
                    activeCanvas().drawText("1200 (Bank)", MARGIN_LEFT + 60f, yPos + 11f, textPaintNormal)
                    val nameStr = textPaintNormal.truncate(data.third, 200f)
                    activeCanvas().drawText(nameStr, MARGIN_LEFT + 130f, yPos + 11f, textPaintNormal)
                    activeCanvas().drawText("${data.first}", MARGIN_LEFT + 340f, yPos + 11f, textPaintNormal)
                    activeCanvas().drawText("19%", MARGIN_LEFT + 390f, yPos + 11f, textPaintNormal)
                    val sumStr = currencyFormatter.format(data.second)
                    val sumWidth = textPaintBold.measureText(sumStr)
                    activeCanvas().drawText(sumStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - sumWidth, yPos + 11f, textPaintBold)
                    yPos += 16f
                }
                yPos += 20f
            }

            checkSpaceAndPageBreak(40f)
            activeCanvas().drawText("Einzelaufstellung der erfassten Belege ($taxYear)", MARGIN_LEFT, yPos + 10f, sectionTitlePaint)
            yPos += 18f

            checkSpaceAndPageBreak(30f)
            activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, tableHeaderBg)
            activeCanvas().drawText("Datum", MARGIN_LEFT + 6f, yPos + 12f, tableHeaderPaint)
            activeCanvas().drawText("Aussteller / Kreditor", MARGIN_LEFT + 70f, yPos + 12f, tableHeaderPaint)
            activeCanvas().drawText("Beschreibung / Unterkategorie", MARGIN_LEFT + 200f, yPos + 12f, tableHeaderPaint)
            activeCanvas().drawText("Konto", MARGIN_LEFT + 410f, yPos + 12f, tableHeaderPaint)
            val amtHeaderStr = "Betrag (€)"
            val amtHeaderWidth = tableHeaderPaint.measureText(amtHeaderStr)
            activeCanvas().drawText(amtHeaderStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - amtHeaderWidth, yPos + 12f, tableHeaderPaint)
            yPos += 18f

            filteredReceipts.forEachIndexed { idx, r ->
                checkSpaceAndPageBreak(18f)
                val rBg = if (idx % 2 == 0) rowBgEven else rowBgOdd
                activeCanvas().drawRect(MARGIN_LEFT, yPos, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, rBg)
                activeCanvas().drawLine(MARGIN_LEFT, yPos + 18f, PAGE_WIDTH - MARGIN_RIGHT, yPos + 18f, rowBorderPaint)

                val dateFormatted = try {
                    val inputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val outputFmt = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                    inputFmt.parse(r.datum)?.let { outputFmt.format(it) } ?: r.datum
                } catch (e: Exception) {
                    r.datum
                }

                activeCanvas().drawText(dateFormatted, MARGIN_LEFT + 6f, yPos + 12f, textPaintNormal)
                val ausstellerStr = textPaintBold.truncate(r.aussteller, 120f)
                activeCanvas().drawText(ausstellerStr, MARGIN_LEFT + 70f, yPos + 12f, textPaintBold)
                val descText = if (r.beschreibung.isNotEmpty()) r.beschreibung else r.unterkategorie
                val descStr = textPaintNormal.truncate(descText, 200f)
                activeCanvas().drawText(descStr, MARGIN_LEFT + 200f, yPos + 12f, textPaintNormal)
                val kontoStr = textPaintNormal.truncate(r.kontoNr, 45f)
                activeCanvas().drawText(kontoStr, MARGIN_LEFT + 410f, yPos + 12f, textPaintNormal)
                val amountStr = currencyFormatter.format(r.bruttobetrag)
                val amountWidth = textPaintBold.measureText(amountStr)
                val amountPaint = Paint(textPaintBold).apply {
                    if (r.isEigenleistungSanierung) color = 0xFF059669.toInt()
                }
                activeCanvas().drawText(amountStr, PAGE_WIDTH - MARGIN_RIGHT - 6f - amountWidth, yPos + 12f, amountPaint)
                yPos += 18f
            }

            currentPage?.let { document.finishPage(it) }
            currentPage = null

            val filename = if (includeTaxAdvisorSummary) "Steuerbericht_Steuerberater_${taxYear}.pdf" else "Steuerbericht_Finanzamt_${taxYear}.pdf"
            val pdfFile = File(context.cacheDir, filename)
            FileOutputStream(pdfFile).use { document.writeTo(it) }
            document.close()
            val fileSizeKb = pdfFile.length() / 1024
            Log.d(TAG, "PDF successfully generated at: ${pdfFile.absolutePath} (Size: ${fileSizeKb} KB)")
            pdfFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate PDF", e)
            try {
                currentPage?.let { document.finishPage(it) }
            } catch (_: Exception) {
            }
            try {
                document.close()
            } catch (_: Exception) {
            }
            null
        }
    }

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

    /**
     * Creates the ordered annual start document for the tax advisor package.
     * It intentionally uses descriptive sections instead of fixed ELSTER line numbers.
     */
    fun createAdvisorStartPdf(summary: AdvisorAnnualSummary): ByteArray {
        val readiness = AdvisorPackageReadinessEvaluator.evaluate(summary)
        val document = PdfDocument()
        val output = java.io.ByteArrayOutputStream()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        val title = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val heading = Paint().apply {
            color = 0xFF0F172A.toInt()
            textSize = 12f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }
        val body = Paint().apply {
            color = 0xFF334155.toInt()
            textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }
        val status = Paint().apply {
            color = if (readiness.ready) 0xFF047857.toInt() else 0xFFB91C1C.toInt()
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page!!.canvas
            y = 44f
            canvas!!.drawText(
                "Steuerberater-Jahresabschlusspaket ${summary.year}",
                MARGIN_LEFT,
                25f,
                body
            )
        }

        fun line(text: String, paint: Paint = body, indent: Float = 0f) {
            if (y > PAGE_HEIGHT - 55f) newPage()
            val maxWidth = CONTENT_WIDTH - indent
            var safe = text
            while (safe.isNotEmpty() && paint.measureText(safe) > maxWidth) {
                safe = safe.dropLast(1)
            }
            if (safe.length < text.length) safe = safe.dropLast(3.coerceAtMost(safe.length)) + "..."
            canvas!!.drawText(safe, MARGIN_LEFT + indent, y, paint)
            y += if (paint === heading) 18f else 13f
        }

        fun section(number: Int, name: String, lines: List<String>) {
            y += 5f
            line("$number. $name", heading)
            if (lines.isEmpty()) line("Keine Angaben vorhanden.", body, 10f)
            else lines.forEach { line("• $it", body, 10f) }
        }

        return try {
            newPage()
            line("Jahresübersicht für den Steuerberater", title)
            line(readiness.status, status)
            line("Objekt: ${summary.propertyTitle}")
            line("Einnahmen: ${String.format(Locale.GERMANY, "%.2f", summary.totalIncome)} EUR")
            line("Werbungskosten: ${String.format(Locale.GERMANY, "%.2f", summary.totalExpenses)} EUR")
            line("Vorläufiges Ergebnis: ${String.format(Locale.GERMANY, "%.2f", summary.result)} EUR")
            line("Vorbereitungshilfe – keine Steuerberatung; keine festen ELSTER-Zeilennummern.")
            section(1, "Objektübersicht", summary.propertyOverview)
            section(2, "Finanzierung", summary.financing)
            section(3, "Mieten", summary.rentOverview)
            section(4, "Sanierungen", summary.renovationsAndAfa)
            section(5, "AfA / 15-%-Monitor", summary.renovationsAndAfa)
            section(
                6,
                "Werbungskosten",
                summary.expenseValues.map {
                    "${it.label}: ${String.format(Locale.GERMANY, "%.2f", it.amount)} EUR – ${it.checkStatus}"
                }
            )
            section(7, "Offene Prüfhinweise", summary.openIssues)
            section(8, "Beigefügte Originalunterlagen", summary.attachedOriginalDocuments)
            if (readiness.blockers.isNotEmpty()) {
                section(9, "Blockierende Punkte", readiness.blockers)
            }
            page?.let(document::finishPage)
            document.writeTo(output)
            output.toByteArray()
        } finally {
            document.close()
            output.close()
        }
    }

}
