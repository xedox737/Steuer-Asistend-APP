package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.*

internal data class RentReviewPayment(
    val key: String, val kind: String, val amount: Double, val date: String,
    val partner: String, val purpose: String, val propertyHint: String, val unitHint: String,
    val receipt: Receipt? = null, val transaction: BankTransaction? = null
)

internal object RentPaymentReview {
    fun build(
        groups: List<RentPropertyUnits>, receipts: List<Receipt>,
        transactions: List<BankTransaction>, assignments: List<BankRentAssignment>,
        links: List<BankReceiptLink>, suggestions: Map<String, List<BankRentSuggestion>> = emptyMap()
    ): List<RentReviewPayment> {
        val propertyIds = groups.map { it.property.propertyId }.toSet()
        fun propertyName(id: String) = groups.firstOrNull { it.property.propertyId == id }?.property?.name.orEmpty()
        val receiptRows = receipts.filter { receipt ->
            isRentalIncomeReceipt(receipt) && receipt.deletionStatus in setOf("", "ACTIVE") &&
                receipt.bruttobetrag.isFinite() && receipt.exportStatus != "AUSGESCHLOSSEN" &&
                (!isConfirmedRentalIncomeReceipt(receipt) || groups.none { group ->
                    group.property.propertyId == receipt.propertyId && group.units.any { unit ->
                        if (receipt.unitId.isNotBlank()) receipt.unitId == PropertyUnitScopedData.stableUnitId(group.property.propertyId, unit)
                        else receipt.wohneinheit.isNotBlank() && receipt.wohneinheit in setOf(unit.name, unit.label)
                    }
                })
        }.map { receipt ->
            RentReviewPayment("receipt-${receipt.id}", "Beleg prüfen", receipt.bruttobetrag, receipt.datum,
                receipt.aussteller, receipt.beschreibung, propertyName(receipt.propertyId), receipt.wohneinheit, receipt = receipt)
        }
        val bankRows = transactions.filter { tx ->
            tx.isIncome && tx.amount.isFinite() && tx.classification == BankTransactionClassification.NORMAL &&
                tx.reconciliationStatus in setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.REVIEW, BankReconciliationStatus.PARTIAL) &&
                (tx.propertyId.isBlank() || tx.propertyId in propertyIds)
        }.mapNotNull { tx ->
            val txAssignments = assignments.filter { it.transactionId == tx.transactionId }
            val review = txAssignments.filter { it.status == BankRentAssignmentStatus.REVIEW &&
                it.paymentType in setOf(BankSplitPaymentType.RENT, BankSplitPaymentType.UTILITIES_PREPAYMENT,
                    BankSplitPaymentType.UTILITIES_SETTLEMENT, RentPaymentType.NEBENKOSTEN) }
            val candidate = suggestions[tx.transactionId].orEmpty().firstOrNull { it.propertyId in propertyIds }
            val type = RentPaymentClassifier.classify(tx)
            val rentalCategory = tx.category == "Miete, Nebenkosten & Kaution" ||
                (tx.category == "Sonstige Einnahmen" && tx.subcategory.contains("Miet", true))
            if (type == RentPaymentType.KAUTION ||
                (tx.category.isNotBlank() && !rentalCategory && review.isEmpty()) ||
                (type == RentPaymentType.UNKLAR && !rentalCategory && review.isEmpty() && candidate == null)) return@mapNotNull null
            val remaining = RentPaymentProjection.remainingAmount(tx,
                receipts.filter { it.deletionStatus in setOf("", "ACTIVE") }, links, txAssignments)
            if (remaining <= .01 && review.isEmpty()) return@mapNotNull null
            val partiallyAssigned = remaining < tx.amount - .01
            RentReviewPayment("bank-${tx.transactionId}", if (partiallyAssigned) "Teilzugeordnete Bankzahlung" else "Bankzahlung prüfen",
                if (remaining > .01) remaining else review.sumOf { it.allocatedAmount }.coerceIn(0.0, tx.amount),
                tx.bookingDate, tx.counterparty, tx.purpose,
                propertyName(tx.propertyId).ifBlank { candidate?.propertyLabel.orEmpty() },
                groups.flatMap { it.units }.firstOrNull { it.unitId == tx.unitId }?.label.orEmpty()
                    .ifBlank { candidate?.unitName.orEmpty() }, transaction = tx)
        }
        return (receiptRows + bankRows).sortedWith(compareByDescending<RentReviewPayment> { it.date }.thenBy { it.key })
    }
}

@Composable
internal fun RentPaymentReviewDialog(payments: List<RentReviewPayment>, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, shape = Ui2.shape,
        title = { Text("Zahlungen prüfen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp).testTag("rent_review_list"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Diese offenen Beträge zählen erst nach eindeutiger Bestätigung als Mietzahlung.") }
                items(payments, key = { it.key }) { payment ->
                    Card(Modifier.fillMaxWidth().testTag("rent_review_${payment.key}").clickable {
                        onDismiss()
                        payment.receipt?.let { viewModel.openReceiptDetail(it.id) }
                        payment.transaction?.let { viewModel.openBankTransactionDetails(it.transactionId) }
                    }, shape = Ui2.controlShape) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${payment.kind} · ${NumberFormatter.format(payment.amount)}")
                            Text("${payment.date} · ${payment.partner.ifBlank { "Zahlungspartner unbekannt" }}")
                            if (payment.purpose.isNotBlank()) Text(payment.purpose)
                            Text(listOf(payment.propertyHint, payment.unitHint).filter(String::isNotBlank).joinToString(" · ")
                                .ifBlank { "Immobilie / Einheit noch unklar" })
                            if (payment.transaction != null) Text("Zuordnung noch offen; mögliche Einheit ist ein Hinweis.", color = WarmOrange)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } })
}
