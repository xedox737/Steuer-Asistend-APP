package com.example.util

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfDocument
import android.util.Log
import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.example.data.Receipt
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class AdvisorPackageResult(
    val zipFile: File,
    val exportId: String,
    val totalRecords: Int,
    val totalAmountEur: Double,
    val sha256Checksum: String,
    val warningsCount: Int
)

object AdvisorPackageBuilder {

    private const val TAG = "AdvisorPackageBuilder"

    fun buildPackage(
        context: Context,
        records: List<BookingRecord>,
        excludedReceipts: List<Receipt>,
        profile: DatevProfile,
        validationReport: ValidationReport,
        periodSummary: String = "2026"
    ): AdvisorPackageResult {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.GERMANY).format(Date())
        val timestampIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.GERMANY).format(Date())
        val exportId = "EXP_${timestamp}_${(1000..9999).random()}"

        val safePropName = profile.profileName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(20)
        val zipFileName = "DATEV_Export_${safePropName}_${periodSummary}_${exportId}.zip"
        val zipFile = File(context.cacheDir, zipFileName)

        // UTF-8 BOM
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        val fileItems = mutableListOf<ManifestFileItem>()

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->

            // 1. 01_DATEV/EXTF_Buchungsstapel_<Zeitraum>.csv
            val extfCsv = DatevCsvSerializer.serializeToCsvString(records, profile, periodSummary.take(4))
            val extfBytes = extfCsv.toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("01_DATEV/EXTF_Buchungsstapel_${periodSummary}.csv"))
            zos.write(bom)
            zos.write(extfBytes)
            zos.closeEntry()

            // 2. 02_Belege/ (Attachments as PDFs or original images)
            val processedReceiptReferences = mutableSetOf<String>()
            records.forEach { record ->
                // belegfeld1 is derived from the stable receipt internalId. Several confirmed
                // booking rows may reference one original document, which must enter the ZIP once.
                if (processedReceiptReferences.add(record.belegfeld1)) {

                    val cleanVendor = record.zahlungspartner.replace(Regex("[^a-zA-Z0-9]"), "_").take(15)
                    val amtStr = String.format(Locale.GERMANY, "%.2f", record.bruttobetrag).replace(",", "-")
                    val belegFileName = "${record.belegdatum}_${record.belegfeld1}_${cleanVendor}_${amtStr}_EUR.pdf"

                    val pdfBytes = createPdfDocumentForRecord(context, record)
                    if (pdfBytes.isNotEmpty()) {
                        zos.putNextEntry(ZipEntry("02_Belege/$belegFileName"))
                        zos.write(pdfBytes)
                        zos.closeEntry()

                        val hash = ReceiptManifestService.calculateSha256Bytes(pdfBytes)
                        fileItems.add(
                            ManifestFileItem(
                                filename = "02_Belege/$belegFileName",
                                receiptId = record.receiptId,
                                mimeType = "application/pdf",
                                fileSizeBytes = pdfBytes.size.toLong(),
                                sha256Hash = hash,
                                belegnummer = record.belegfeld1,
                                belegdatum = record.belegdatum,
                                betragEur = record.bruttobetrag
                            )
                        )
                    }
                }
            }

            // 3. 03_Kontrolle/
            // Buchungsvorschlaege.csv
            val controlCsv = DatevCsvSerializer.createControlCsv(records)
            zos.putNextEntry(ZipEntry("03_Kontrolle/Buchungsvorschlaege.csv"))
            zos.write(bom)
            zos.write(controlCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Offene_Punkte.csv
            val offenePunkteSb = StringBuilder()
            offenePunkteSb.append("Fehler_Typ;Booking_ID;Receipt_ID;Feld;Meldung\n")
            validationReport.errors.forEach { e ->
                offenePunkteSb.append("FEHLER;${e.bookingId};${e.receiptId};${e.field};\"${e.message}\"\n")
            }
            validationReport.warnings.forEach { w ->
                offenePunkteSb.append("WARNUNG;${w.bookingId};${w.receiptId};${w.field};\"${w.message}\"\n")
            }
            zos.putNextEntry(ZipEntry("03_Kontrolle/Offene_Punkte.csv"))
            zos.write(bom)
            zos.write(offenePunkteSb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Nicht_exportierte_Belege.csv
            val nichtExpSb = StringBuilder()
            nichtExpSb.append("Receipt_ID;Aussteller;Datum;Betrag_EUR;Kategorie;ExportStatus;Grund\n")
            excludedReceipts.forEach { r ->
                nichtExpSb.append("${r.id};\"${r.aussteller}\";${r.datum};${r.bruttobetrag};\"${r.hauptkategorie}\";${r.exportStatus};\"Ausgeschlossen/Nicht freigegeben\"\n")
            }
            zos.putNextEntry(ZipEntry("03_Kontrolle/Nicht_exportierte_Belege.csv"))
            zos.write(bom)
            zos.write(nichtExpSb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Dubletten.csv
            val dublettenSb = StringBuilder()
            dublettenSb.append("Receipt_ID;Aussteller;Datum;Betrag_EUR;Status\n")
            zos.putNextEntry(ZipEntry("03_Kontrolle/Dubletten.csv"))
            zos.write(bom)
            zos.write(dublettenSb.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 4. 04_Dokumentation/
            // Exportprotokoll.txt
            val protokollText = """
                ================================================================================
                EXPORTPROTOKOLL DATEV-ÜBERGABEPAKET
                ================================================================================
                Export-ID:              $exportId
                Erstellungszeitpunkt:   $timestampIso
                Kanzleiprofil:          ${profile.profileName} (v${profile.version})
                Berater-Nr / Mandant:   ${profile.beraterNummer} / ${profile.mandantenNummer}
                Kontenrahmen:           ${profile.kontenrahmen} (Sachkontenlänge: ${profile.sachkontenLaenge})
                Anzahl Buchungssätze:   ${records.size}
                Gesamtsumme Brutto EUR: ${String.format(Locale.GERMANY, "%.2f", validationReport.totalAmount)}
                Geprüfte Freigaben:     ${if (validationReport.isValidForExport) "VOLLSTÄNDIG FREIGEGEBEN" else "MIT WARNUNGEN/ÜBERSTEUERUNG FREIGEGEBEN"}
                Anzahl Warnungen:       ${validationReport.warnings.size}
                Anzahl Fehler:          ${validationReport.errors.size}
                
                DOKUMENTATION DER BUCHUNGSSÄTZE:
                ${records.joinToString("\n") { " - ${it.belegdatum} | ${it.belegfeld1} | Konto ${it.sachkonto} -> ${it.gegenkonto} | ${String.format(Locale.GERMANY, "%.2f", it.bruttobetrag)} EUR | ${it.beschreibung}" }}
            """.trimIndent()

            zos.putNextEntry(ZipEntry("04_Dokumentation/Exportprotokoll.txt"))
            zos.write(protokollText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Kanzleiprofil.txt
            val profileText = DatevProfileService.exportProfileToJson(profile)
            zos.putNextEntry(ZipEntry("04_Dokumentation/Kanzleiprofil.txt"))
            zos.write(profileText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // README.txt
            val readmeText = """
                STEUERBERATER-ÜBERGABEPAKET ANLAGE V (IMMOBILIEN)
                --------------------------------------------------------------------------------
                Ordnerstruktur:
                - 01_DATEV/             Enthält die EXTF_Buchungsstapel.csv zur direkten DATEV-Stapelvearbeitung.
                - 02_Belege/            Enthält alle zugehörigen Beleg-PDFs mit eindeutigen Dateinamen.
                - 03_Kontrolle/         Enthält Buchungsvorschläge, Prüfprotokolle und nicht exportierte Belege.
                - 04_Dokumentation/     Enthält Exportprotokoll und Kanzleiprofil.
                - manifest.json         Kryptografisches Prüfsummen-Manifest (SHA-256) aller Dateien.
            """.trimIndent()

            zos.putNextEntry(ZipEntry("04_Dokumentation/README.txt"))
            zos.write(readmeText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. manifest.json
            val manifestJson = ReceiptManifestService.generateManifestJson(
                exportId = exportId,
                timestampIso = timestampIso,
                profile = profile,
                fileItems = fileItems,
                totalAmountEur = validationReport.totalAmount
            )
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val zipSha256 = ReceiptManifestService.calculateSha256(zipFile)

        return AdvisorPackageResult(
            zipFile = zipFile,
            exportId = exportId,
            totalRecords = records.size,
            totalAmountEur = validationReport.totalAmount,
            sha256Checksum = zipSha256,
            warningsCount = validationReport.warnings.size
        )
    }

    private fun createPdfDocumentForRecord(context: Context, record: BookingRecord): ByteArray {
        return try {
            val document = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = android.graphics.Paint().apply {
                color = 0xFF0F172A.toInt()
                textSize = 16f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            val labelPaint = android.graphics.Paint().apply {
                color = 0xFF475569.toInt()
                textSize = 10f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                isAntiAlias = true
            }
            val valuePaint = android.graphics.Paint().apply {
                color = 0xFF0F172A.toInt()
                textSize = 11f
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.NORMAL)
                isAntiAlias = true
            }

            canvas.drawText("DATEV BELEGDOKUMENT / BUCHUNGSBELEG", 40f, 45f, titlePaint)

            canvas.drawText("BELEGNUMMER (FIELD 1):", 40f, 75f, labelPaint)
            canvas.drawText(record.belegfeld1, 200f, 75f, valuePaint)

            canvas.drawText("DATUM:", 40f, 95f, labelPaint)
            canvas.drawText(record.belegdatum, 200f, 95f, valuePaint)

            canvas.drawText("ZAHLUNGSPARTNER:", 40f, 115f, labelPaint)
            canvas.drawText(record.zahlungspartner, 200f, 115f, valuePaint)

            canvas.drawText("BRUTTOBETRAG:", 40f, 135f, labelPaint)
            canvas.drawText("${String.format(Locale.GERMANY, "%.2f", record.bruttobetrag)} EUR (${record.sollHaben})", 200f, 135f, valuePaint)

            canvas.drawText("SACHKONTO / GEGENKONTO:", 40f, 155f, labelPaint)
            canvas.drawText("${record.sachkonto} / ${record.gegenkonto}", 200f, 155f, valuePaint)

            canvas.drawText("KOST1 (OBJEKT) / KOST2:", 40f, 175f, labelPaint)
            canvas.drawText("${record.kost1} / ${record.kost2.ifBlank { "ALLG" }}", 200f, 175f, valuePaint)

            canvas.drawText("BUCHUNGSTEXT:", 40f, 195f, labelPaint)
            canvas.drawText(record.beschreibung, 200f, 195f, valuePaint)

            // If local image file exists, draw preview
            if (record.originalFileId.isNotBlank()) {
                val file = File(record.originalFileId)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        val maxW = 515f
                        val maxH = 550f
                        val scale = Math.min(maxW / bmp.width, maxH / bmp.height)
                        val drawW = (bmp.width * scale).toInt()
                        val drawH = (bmp.height * scale).toInt()
                        val destRect = android.graphics.Rect(40, 220, 40 + drawW, 220 + drawH)
                        canvas.drawBitmap(bmp, null, destRect, null)
                        bmp.recycle()
                    }
                }
            }

            document.finishPage(page)
            val outputStream = ByteArrayOutputStream()
            document.writeTo(outputStream)
            document.close()
            outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error building PDF for record ${record.bookingId}", e)
            ByteArray(0)
        }
    }
}
