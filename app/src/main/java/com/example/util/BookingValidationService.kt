package com.example.util

import com.example.data.BookingRecord
import com.example.data.DatevProfile
import com.example.data.Receipt
import java.io.File

data class ValidationIssue(
    val isError: Boolean, // true = BLOCKING ERROR, false = WARNING
    val bookingId: String,
    val receiptId: Int,
    val field: String,
    val message: String
)

data class ValidationReport(
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
    val totalRecords: Int,
    val totalAmount: Double,
    val isValidForExport: Boolean
)

object BookingValidationService {

    fun validateRecords(
        records: List<BookingRecord>,
        profile: DatevProfile,
        allowUnverifiedExport: Boolean = false
    ): ValidationReport {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()

        var totalAmount = 0.0

        records.forEach { record ->
            totalAmount += record.bruttobetrag

            // 1. Account length & presence check
            if (record.sachkonto.isBlank()) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "sachkonto",
                        message = "Sachkonto fehlt für Beleg #${record.receiptId} (${record.zahlungspartner})"
                    )
                )
            } else if (record.sachkonto.length != profile.sachkontenLaenge) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "sachkonto",
                        message = "Sachkonto '${record.sachkonto}' entspricht nicht der eingestellten Sachkontenlänge (${profile.sachkontenLaenge} Stellen)"
                    )
                )
            }

            if (record.gegenkonto.isBlank()) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "gegenkonto",
                        message = "Gegenkonto fehlt für Beleg #${record.receiptId}"
                    )
                )
            }

            // 2. Cost category presence check
            if (record.hauptkategorie.isBlank() || record.hauptkategorie == "OHNE_KOSTENART") {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "hauptkategorie",
                        message = "Kostenart / Kategorie fehlt für Beleg #${record.receiptId}"
                    )
                )
            }

            // 3. Amount check
            if (record.bruttobetrag <= 0.0) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "bruttobetrag",
                        message = "Buchungsbetrag ist Null oder negativ (Betrag muss > 0 sein)"
                    )
                )
            }

            // 4. Belegnummer check
            if (record.belegfeld1.isBlank()) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "belegfeld1",
                        message = "Belegnummer (Belegfeld 1) ist leer"
                    )
                )
            }

            // 5. Date check
            if (!record.belegdatum.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "belegdatum",
                        message = "Ungültiges Datumsformat '${record.belegdatum}' (erwartet: YYYY-MM-DD)"
                    )
                )
            }

            // 6. Verification status
            val isUnverified = record.pruefstatus == "UNGEPRUEFT" ||
                    record.exportStatus in listOf("ENTWURF", "KI_VORSCHLAG", "ZU_PRUEFEN")

            if (isUnverified) {
                if (!allowUnverifiedExport) {
                    errors.add(
                        ValidationIssue(
                            isError = true,
                            bookingId = record.bookingId,
                            receiptId = record.receiptId,
                            field = "pruefstatus",
                            message = "Beleg #${record.receiptId} ist noch ungeprüft/KI-Vorschlag. Bitte erst als 'geprüft' freigeben."
                        )
                    )
                } else {
                    warnings.add(
                        ValidationIssue(
                            isError = false,
                            bookingId = record.bookingId,
                            receiptId = record.receiptId,
                            field = "pruefstatus",
                            message = "Beleg #${record.receiptId} wird ungeprüft exportiert."
                        )
                    )
                }
            }

            if (record.exportStatus == "EXPORTIERT") {
                warnings.add(
                    ValidationIssue(
                        isError = false,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "exportStatus",
                        message = "Beleg #${record.receiptId} wurde bereits früher exportiert."
                    )
                )
            }

            // 7. Attached file warning
            if (record.originalFileId.isNotBlank()) {
                val file = File(record.originalFileId)
                if (!file.exists() && !record.originalFileId.startsWith("http")) {
                    warnings.add(
                        ValidationIssue(
                            isError = false,
                            bookingId = record.bookingId,
                            receiptId = record.receiptId,
                            field = "originalFileId",
                            message = "Belegdatei #${record.receiptId} konnte lokal nicht gefunden werden."
                        )
                    )
                }
            } else {
                warnings.add(
                    ValidationIssue(
                        isError = false,
                        bookingId = record.bookingId,
                        receiptId = record.receiptId,
                        field = "originalFileId",
                        message = "Keine Belegdatei an Beleg #${record.receiptId} angehängt."
                    )
                )
            }
        }

        if (records.isNotEmpty()) {
            val exportYear = records.first().belegdatum.take(4)
                .takeIf { it.matches(Regex("\\d{4}")) }
                ?: profile.wirtschaftsjahrBeginn.take(4)
            val serializedCsv = DatevCsvSerializer.serializeToCsvString(records, profile, exportYear)
            DatevFormatValidator.validate(serializedCsv).errors.forEach { formatError ->
                errors.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = "DATEV_FORMAT",
                        receiptId = 0,
                        field = "datevFormat",
                        message = formatError
                    )
                )
            }
        }

        return ValidationReport(
            errors = errors,
            warnings = warnings,
            totalRecords = records.size,
            totalAmount = Math.round(totalAmount * 100.0) / 100.0,
            isValidForExport = errors.isEmpty()
        )
    }

    /**
     * Validates that the sum of generated export records matches the receipt total amount.
     */
    fun validateReceiptSum(receipt: Receipt, records: List<BookingRecord>, profile: DatevProfile): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()

        val receiptAbsTotal = Math.round(Math.abs(receipt.bruttobetrag) * 100.0)
        val recordsTotalCents = Math.round(records.sumOf { it.bruttobetrag } * 100.0)

        // Check unallocated items
        val allocations = DatevMappingService.buildAllocationsFromReceipt(receipt, profile)
        if (allocations.any { it.isUnallocated || it.costCategory.isBlank() }) {
            issues.add(
                ValidationIssue(
                    isError = true,
                    bookingId = "REC_${receipt.id}",
                    receiptId = receipt.id,
                    field = "hauptkategorie",
                    message = "Beleg #${receipt.id} enthält unvollständig zugeordnete Positionen/Kostenarten."
                )
            )
        }

        if (profile.privateShareExportMode != "EXCLUDE_WITH_DOCUMENTATION") {
            if (Math.abs(receiptAbsTotal - recordsTotalCents) > 1) { // > 1 cent tolerance
                issues.add(
                    ValidationIssue(
                        isError = true,
                        bookingId = "REC_${receipt.id}",
                        receiptId = receipt.id,
                        field = "bruttobetrag",
                        message = "Die Summe der DATEV-Buchungszeilen (${recordsTotalCents / 100.0} €) entspricht nicht dem Belegbetrag (${receiptAbsTotal / 100.0} €)."
                    )
                )
            }
        }

        return issues
    }
}
