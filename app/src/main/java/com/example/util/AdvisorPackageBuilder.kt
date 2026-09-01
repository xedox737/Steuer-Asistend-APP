package com.example.util

import android.content.Context
import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.example.data.Receipt
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
        includedReceipts: List<Receipt>,
        excludedReceipts: List<Receipt>,
        includeOriginals: Boolean,
        profile: DatevProfile,
        validationReport: ValidationReport,
        periodSummary: String = "2026",
        annualSummary: AdvisorAnnualSummary? = null
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
            val formatValidation = DatevFormatValidator.validate(extfCsv)
            require(formatValidation.isValid) {
                "DATEV-Export wegen Formatfehlern blockiert: " +
                    formatValidation.errors.joinToString(" | ")
            }
            val extfBytes = extfCsv.toByteArray(Charsets.UTF_8)
            zos.putNextEntry(ZipEntry("01_DATEV/EXTF_Buchungsstapel_${periodSummary}.csv"))
            zos.write(bom)
            zos.write(extfBytes)
            zos.closeEntry()

            // 2. 02_Belege/ (exactly one verified original per stable receipt identity)
            val includedByRoomId = includedReceipts.associateBy { it.id }
            val processedReceiptReferences = mutableSetOf<String>()
            if (includeOriginals) records.forEach { record ->
                if (processedReceiptReferences.add(record.belegfeld1)) {
                    val receipt = includedByRoomId[record.receiptId]
                        ?: throw IllegalStateException(
                            "Exportierter Buchungssatz hat keinen zugehörigen Beleg."
                        )
                    val attachment = DatevOriginalAttachmentPolicy.resolve(receipt)
                        ?: throw IllegalStateException(
                            "Originalbeleg für " + receipt.getEffectiveDisplayId() +
                                " ist nicht lokal verfügbar oder nicht lesbar."
                        )
                    val belegFileName =
                        record.belegdatum + "_" + record.belegfeld1 + "_Original." +
                            attachment.extension
                    val entryName = "02_Belege/" + belegFileName
                    val originalBytes = attachment.file.readBytes()

                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(originalBytes)
                    zos.closeEntry()

                    fileItems.add(
                        ManifestFileItem(
                            filename = entryName,
                            receiptId = receipt.id,
                            mimeType = attachment.mimeType,
                            fileSizeBytes = originalBytes.size.toLong(),
                            sha256Hash = ReceiptManifestService.calculateSha256Bytes(originalBytes),
                            belegnummer = record.belegfeld1,
                            belegdatum = record.belegdatum,
                            betragEur = receipt.bruttobetrag
                        )
                    )
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
                - 05_Jahresabschluss/   Enthält Jahresübersicht, Objekt, Finanzierung, Mieten, AfA/15 %, Anlage-V-Vorschau und offene Prüfpunkte.
                - manifest.json         DATEV-/Beleg-Manifest; der Jahresabschluss enthält zusätzlich eine eigene SHA-256-Prüfsummenliste.
            """.trimIndent()

            zos.putNextEntry(ZipEntry("04_Dokumentation/README.txt"))
            zos.write(readmeText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 5. 05_Jahresabschluss/ - Jahresübersicht, Finanzierung, Mieten, AfA/15 % und Anlage-V-Vorschau
            annualSummary?.let { summary ->
                AdvisorAnnualPackageContentBuilder.buildEntries(summary).forEach { (entryName, bytes) ->
                    zos.putNextEntry(ZipEntry(entryName))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }

            // 6. manifest.json
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

}
