package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RentBankWorkflowRegressionTest {
    private lateinit var context: Context
    private val month = YearMonth.of(2026, 10)
    private val unit = WohneinheitStatus("WE 01", "WE 01", "Vermietet", "Mieter", 690.0, 60.0, "2026-01-01", "unit-a")

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        TenantHistoryStore.save(context, "property-a", "unit-a", unit.name,
            listOf(TenantPeriod(1, unit.name, "Mieter", "2026-01-01", "", 690.0, 200.0, 0.0)))
    }

    @Test fun linkedPartialPaymentDoesNotFillUnassignedBankRemainder() {
        val row = project(listOf(receipt(500.0)), listOf(assignment(500.0)), listOf(link(500.0)))
        assertEquals(500.0, row.actual, .001)
        assertEquals(390.0, row.missing, .001)
        assertEquals(RentPaymentStatus.PARTIAL, row.status)
    }

    @Test fun linkedRentAndSeparateConfirmedUtilitiesKeepBothComponents() {
        val row = project(listOf(receipt(690.0)), listOf(assignment(200.0, BankSplitPaymentType.UTILITIES_PREPAYMENT)), listOf(link(690.0)))
        assertEquals(890.0, row.actual, .001)
        assertEquals(RentPaymentStatus.PAID, row.status)
    }

    @Test fun linkedRentAndSplitAssignmentsCountRentOnceAndUtilitiesOnce() {
        val row = project(listOf(receipt(690.0)), listOf(assignment(690.0), assignment(200.0, BankSplitPaymentType.UTILITIES_PREPAYMENT)), listOf(link(690.0)))
        assertEquals(890.0, row.actual, .001)
    }

    @Test fun directReceiptReferenceAndSeparateUtilitiesKeepTheUnlinkedComponent() {
        val row = project(listOf(receipt(500.0)), listOf(assignment(500.0).copy(receiptId = 1), assignment(200.0, BankSplitPaymentType.UTILITIES_PREPAYMENT)), listOf(link(500.0)))
        assertEquals(700.0, row.actual, .001)
        assertEquals(190.0, row.missing, .001)
    }

    @Test fun combinedReceiptAndBankPartialAreOnePayment() {
        val row = project(listOf(receipt(500.0).copy(unterkategorie = "Warmmiete")), listOf(assignment(400.0), assignment(100.0, BankSplitPaymentType.UTILITIES_PREPAYMENT)), listOf(link(500.0)))
        assertEquals(500.0, row.actual, .001)
    }

    @Test fun receiptInternalIdIsAuthoritativeWhenNumericIdPointsElsewhere() {
        val correct = receipt(500.0).copy(id = 7)
        val other = receipt(100.0).copy(internalId = "unrelated")
        val row = project(listOf(correct, other), listOf(assignment(500.0)), listOf(link(500.0)))
        assertEquals(600.0, row.actual, .001)
    }

    @Test fun equalAmountsAndDatesWithoutStableLinkRemainSeparatePayments() {
        val row = project(listOf(receipt(500.0)), listOf(assignment(500.0)), emptyList())
        assertEquals(1000.0, row.actual, .001)
    }

    @Test fun unreviewedReceiptIsNotActualButConfirmedBankPaymentStillCounts() {
        val pending = receipt(890.0).copy(pruefstatus = "UNGEPRUEFT", exportStatus = "KI_VORSCHLAG")
        assertEquals(0.0, project(listOf(pending), emptyList(), emptyList()).actual, .001)
        assertEquals(500.0, project(listOf(pending), listOf(assignment(500.0)), listOf(link(500.0))).actual, .001)
    }

    @Test fun paymentConfirmationDoesNotRequireDatevRelease() {
        val reviewed = receipt(500.0).copy(freigabestatus = "OFFEN", pruefstatus = "KORRIGIERT")
        assertEquals(500.0, project(listOf(reviewed), emptyList(), emptyList()).actual, .001)
        assertEquals("OFFEN", reviewed.freigabestatus)
    }

    @Test fun ignoredTransfersDebitsAndUnconfirmedTransactionsAreNotRentIncome() {
        val invalid = listOf(
            transaction().copy(classification = BankTransactionClassification.PRIVATE_IGNORED),
            transaction().copy(classification = BankTransactionClassification.TRANSFER),
            transaction().copy(amount = -890.0),
            transaction().copy(reconciliationStatus = BankReconciliationStatus.REVIEW),
            transaction().copy(reconciliationStatus = BankReconciliationStatus.OPEN)
        )
        invalid.forEach { assertEquals(0.0, project(emptyList(), listOf(assignment(890.0)), emptyList(), it).actual, .001) }
    }

    @Test fun malformedAssignmentsNeverPoisonValidPaymentOrCreateActualIncome() {
        val invalid = listOf(Double.NaN, Double.POSITIVE_INFINITY, -200.0, 0.0).mapIndexed { index, amount -> assignment(amount).copy(assignmentId = "invalid-$index") }
        assertEquals(500.0, project(emptyList(), invalid + assignment(500.0), emptyList()).actual, .001)
        assertEquals(0.0, project(emptyList(), listOf(assignment(500.0).copy(propertyId = ""), assignment(500.0).copy(unitId = "")), emptyList()).actual, .001)
    }

    @Test fun explicitRentMonthWinsOverBookingMonthAndYear() {
        val october = project(emptyList(), listOf(assignment(890.0)), emptyList(), transaction().copy(bookingDate = "2025-12-31"))
        assertEquals(890.0, october.actual, .001)
        val september = RentTrackingLogic.month(context, "property-a", unit, emptyList(), month.minusMonths(1), listOf(assignment(890.0)), emptyList(), listOf(transaction()))
        assertEquals(0.0, september.actual, .001)
    }

    @Test fun linkedPaymentIsNotCountedAgainInReceiptMonthOrYear() {
        val receipt = receipt(890.0).copy(datum = "2025-12-31")
        val assignments = listOf(assignment(890.0))
        val links = listOf(link(890.0))
        val transactions = listOf(transaction())
        assertEquals(890.0, project(listOf(receipt), assignments, links).actual, .001)
        assertEquals(0.0, RentTrackingLogic.month(context, "property-a", unit, listOf(receipt), YearMonth.of(2025, 12), assignments, links, transactions).actual, .001)
        val group = RentPropertyUnits(PropertyMetadata(propertyId = "property-a"), listOf(unit))
        assertEquals(0.0, RentOverviewPresentation.year(context, listOf(group), listOf(receipt), 2025, assignments, links, transactions).actual, .001)
        assertEquals(890.0, RentOverviewPresentation.year(context, listOf(group), listOf(receipt), 2026, assignments, links, transactions).actual, .001)
    }

    @Test fun oneLinkedReceiptWithTwoExplicitRentMonthsIsDistributedOnlyByConfirmedBankAssignments() {
        val receipt = receipt(1780.0)
        val transaction = transaction().copy(amount = 1780.0)
        val october = assignment(890.0)
        val november = assignment(890.0).copy(assignmentId = "november", rentMonth = "2026-11")
        val assignments = listOf(october, november)
        val links = listOf(link(1780.0))
        val year = RentTrackingLogic.year(context, "property-a", listOf(unit), listOf(receipt), 2026, assignments, links, listOf(transaction)).single()
        assertEquals(890.0, year.months[9].actual, .001)
        assertEquals(890.0, year.months[10].actual, .001)
        assertEquals(1780.0, year.actual, .001)
    }

    @Test fun multipleLinkedReceiptsShareCoverageOnceAndKeepTheirUncoveredAmounts() {
        val first = receipt(400.0)
        val second = receipt(400.0).copy(id = 2, internalId = "second")
        val links = listOf(link(400.0), link(400.0).copy(linkId = "second-link", receiptId = 2, receiptInternalId = "second"))
        assertEquals(800.0, project(listOf(first, second), listOf(assignment(500.0)), links).actual, .001)
    }

    @Test fun proposalSourceCannotMasqueradeAsConfirmedPayment() {
        assertEquals(0.0, project(emptyList(), listOf(assignment(890.0).copy(source = "KI_VORSCHLAG")), emptyList()).actual, .001)
    }

    private fun project(receipts: List<Receipt>, assignments: List<BankRentAssignment>, links: List<BankReceiptLink>, transaction: BankTransaction = transaction()) =
        RentTrackingLogic.month(context, "property-a", unit, receipts, month, assignments, links, listOf(transaction))

    private fun transaction() = BankTransaction("tx", "account", "2026-10-03", amount = 890.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
    private fun assignment(amount: Double, type: String = BankSplitPaymentType.RENT) = BankRentAssignment(
        "assignment-$type", "tx", "property-a", "unit-a", "2026-10", "tenant", amount, type, createdAt = "2026-10-03", updatedAt = "2026-10-03")
    private fun link(amount: Double) = BankReceiptLink("link", "tx", 1, "receipt", amount)
    private fun receipt(amount: Double) = Receipt(
        id = 1, aussteller = "Mieter", datum = "2026-10-03", uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete", kontoNr = "8100", beschreibung = "Miete",
        wohneinheit = "WE 01", propertyId = "property-a", unitId = "unit-a", internalId = "receipt")
}
