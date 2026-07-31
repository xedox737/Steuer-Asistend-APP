package com.example.util

import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

data class ConfirmedDatevAllocation(
    val id: String,
    val amountCent: Long,
    val account: String,
    val counterAccount: String,
    val bookingText: String,
    val confirmed: Boolean,
    val costCenter1: String? = null,
    val taxKey: String? = null
)

data class DatevExportReceipt(
    val internalId: String,
    val amountCent: Long,
    val bookingDate: String,
    val attachmentReference: String?,
    val allocations: List<ConfirmedDatevAllocation>
)

data class DatevExportIssue(
    val receiptInternalId: String,
    val code: String,
    val message: String
)

data class DatevBookingPlanLine(
    val receiptInternalId: String,
    val allocationId: String,
    val amountCent: Long,
    val account: String,
    val counterAccount: String,
    val bookingText: String,
    val receiptGuid: String,
    val costCenter1: String?,
    val taxKey: String?
)

data class DatevAttachmentPlan(
    val receiptInternalId: String,
    val receiptGuid: String,
    val sourceReference: String
)

data class DatevExportPlan(
    val contentId: String,
    val bookingLines: List<DatevBookingPlanLine>,
    val attachments: List<DatevAttachmentPlan>,
    val issues: List<DatevExportIssue>
) {
    val exportable: Boolean get() = issues.isEmpty() && bookingLines.isNotEmpty()
}

/**
 * Safety gate in front of the legacy DATEV writer.
 *
 * It never invents accounts or allocations. A receipt is exportable only when all booking
 * allocations were explicitly confirmed, carry stable IDs and balance to the receipt total.
 */
object DatevExportPolicy {
    private val accountPattern = Regex("""\d{1,9}""")
    private val datePattern = Regex("""\d{4}-\d{2}-\d{2}""")

    fun plan(receipts: List<DatevExportReceipt>, roundingToleranceCent: Long = 1L): DatevExportPlan {
        val issues = mutableListOf<DatevExportIssue>()
        val lines = mutableListOf<DatevBookingPlanLine>()
        val attachments = linkedMapOf<String, DatevAttachmentPlan>()

        val duplicateInternalIds = receipts
            .filter { it.internalId.isNotBlank() }
            .groupingBy { it.internalId }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        receipts.forEach { receipt ->
            val key = receipt.internalId.trim()
            fun reject(code: String, message: String) {
                issues += DatevExportIssue(key, code, message)
            }

            if (key.isBlank()) {
                reject("MISSING_INTERNAL_ID", "Beleg besitzt keine stabile internalId.")
                return@forEach
            }
            if (key in duplicateInternalIds) {
                reject("DUPLICATE_INTERNAL_ID", "internalId kommt im Export mehrfach vor.")
                return@forEach
            }
            if (!datePattern.matches(receipt.bookingDate)) {
                reject("INVALID_BOOKING_DATE", "Buchungsdatum muss YYYY-MM-DD entsprechen.")
            }
            if (receipt.amountCent == 0L) {
                reject("ZERO_RECEIPT_AMOUNT", "Ein Nullbetrag ist nicht exportierbar.")
            }
            if (receipt.allocations.isEmpty()) {
                reject("MISSING_CONFIRMED_ALLOCATION", "Keine bestätigte Kontierung vorhanden.")
                return@forEach
            }
            if (receipt.allocations.any { !it.confirmed }) {
                reject("UNCONFIRMED_ALLOCATION", "Mindestens eine Kontierung ist nicht bestätigt.")
            }
            if (receipt.allocations.any { it.id.isBlank() } ||
                receipt.allocations.map { it.id }.distinct().size != receipt.allocations.size
            ) {
                reject("INVALID_ALLOCATION_ID", "Kontierungen benötigen eindeutige IDs.")
            }
            receipt.allocations.forEach { allocation ->
                if (allocation.amountCent == 0L) {
                    reject("ZERO_ALLOCATION_AMOUNT", "Kontierungsbeträge dürfen nicht null sein.")
                }
                if (receipt.amountCent.sign != allocation.amountCent.sign) {
                    reject("ALLOCATION_SIGN_MISMATCH", "Vorzeichen der Kontierung passt nicht zum Beleg.")
                }
                if (!accountPattern.matches(allocation.account) ||
                    !accountPattern.matches(allocation.counterAccount)
                ) {
                    reject("INVALID_ACCOUNT", "Konto und Gegenkonto müssen numerisch befüllt sein.")
                }
                if (allocation.bookingText.isBlank()) {
                    reject("MISSING_BOOKING_TEXT", "Buchungstext darf nicht leer sein.")
                }
            }
            val difference = receipt.amountCent - receipt.allocations.sumOf { it.amountCent }
            if (abs(difference) > roundingToleranceCent) {
                reject("ALLOCATION_TOTAL_MISMATCH", "Kontierungen weichen um $difference Cent ab.")
            }

            if (issues.none { it.receiptInternalId == key }) {
                val guid = stableReceiptGuid(key)
                receipt.allocations.forEach { allocation ->
                    lines += DatevBookingPlanLine(
                        receiptInternalId = key,
                        allocationId = allocation.id,
                        amountCent = allocation.amountCent,
                        account = allocation.account,
                        counterAccount = allocation.counterAccount,
                        bookingText = allocation.bookingText,
                        receiptGuid = guid,
                        costCenter1 = allocation.costCenter1,
                        taxKey = allocation.taxKey
                    )
                }
                receipt.attachmentReference?.trim()?.takeIf(String::isNotEmpty)?.let { source ->
                    attachments.putIfAbsent(
                        key,
                        DatevAttachmentPlan(key, guid, source)
                    )
                }
            }
        }

        val sortedLines = lines.sortedWith(compareBy(DatevBookingPlanLine::receiptInternalId, DatevBookingPlanLine::allocationId))
        val contentId = contentFingerprint(sortedLines)
        return DatevExportPlan(contentId, sortedLines, attachments.values.toList(), issues)
    }

    fun stableReceiptGuid(internalId: String): String {
        require(internalId.isNotBlank())
        return UUID.nameUUIDFromBytes(
            "steuerassistent-datev:${internalId.trim()}".toByteArray(Charsets.UTF_8)
        ).toString().uppercase(Locale.ROOT)
    }

    private fun contentFingerprint(lines: List<DatevBookingPlanLine>): String {
        val canonical = lines.joinToString("\n") {
            listOf(
                it.receiptInternalId,
                it.allocationId,
                it.amountCent,
                it.account,
                it.counterAccount,
                it.bookingText,
                it.costCenter1.orEmpty(),
                it.taxKey.orEmpty()
            ).joinToString("|")
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private val Long.sign: Int
        get() = when {
            this > 0L -> 1
            this < 0L -> -1
            else -> 0
        }
}
