package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class Phase9CRentArrearsTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val month = YearMonth.of(2026, 10)
    private val property = PropertyMetadata(id = 91, propertyId = "p9c", name = "Haus")
    private fun unit(id: String, rent: Double = 980.0) = WohneinheitStatus(
        "WE 1", "Gleicher Name", "Vermietet", "Mieter $id", rent, 70.0, "2026-10-01", id)
    private val a = unit("a")
    private val b = unit("b")

    @Before fun clear() {
        listOf("rent_plan_prefs", "tenant_history_prefs", "google_drive_prefs").forEach {
            context.getSharedPreferences(it, 0).edit().clear().commit()
        }
    }
    private fun period(u: WohneinheitStatus, start: String = "2026-10-01", end: String = "2026-10-31", pid: String = property.propertyId) {
        TenantHistoryStore.save(context, pid, u.unitId, u.name,
            listOf(TenantPeriod(100 + u.unitId.hashCode().toLong(), u.name, u.mieter, start, end, u.kaltmiete, 0.0, 0.0)))
    }
    private fun receipt(id: Int, u: WohneinheitStatus, amount: Double, date: String = "2026-10-03", pid: String = property.propertyId) =
        Receipt(id, u.mieter, date, "", amount, "Miete, Nebenkosten & Kaution", "Kaltmiete", "8100", "Miete",
            wohneinheit = u.name, propertyId = pid, unitId = u.unitId, internalId = "p9c-$id", pruefstatus = "GEPRUEFT")
    private fun summary(units: List<WohneinheitStatus>, receipts: List<Receipt>,
        assignments: List<BankRentAssignment> = emptyList(), links: List<BankReceiptLink> = emptyList(),
        transactions: List<BankTransaction> = emptyList(), p: PropertyMetadata = property, m: YearMonth = month) =
        ImmobilienManagerProjection.summary(context, p, units, receipts, assignments, links, transactions, m)
    private fun year(units: List<WohneinheitStatus>, receipts: List<Receipt>) =
        RentOverviewPresentation.year(context, listOf(RentPropertyUnits(property, units)), receipts, 2026)

    @Test fun overpaymentNeverHidesAnotherStableUnitArrearsInBothPresentations() {
        listOf(a, b).forEach { period(it) }
        val receipts = listOf(receipt(1, a, 1960.0))
        val summary = summary(listOf(a, b), receipts)
        assertEquals(1960.0, summary.expectedRent, 0.0)
        assertEquals(1960.0, summary.actualRent, 0.0)
        assertEquals(980.0, summary.outstandingRent, 0.0)
        val year = year(listOf(a, b), receipts)
        assertEquals(980.0, year.missing, 0.0)
        assertEquals(980.0, year.rows.sumOf { it.projection.outstanding }, 0.0)
        assertEquals(0.0, year.actual - year.expected, 0.0) // intentional overall balance
        assertNotEquals(year.rows[0].key, year.rows[1].key)
    }

    @Test fun paidPartialMixedAndSingleUnitKeepExpectedResults() {
        val c = unit("c", 500.0)
        listOf(a, b, c).forEach { period(it) }
        assertEquals(0.0, summary(listOf(a, b), listOf(receipt(1, a, 980.0), receipt(2, b, 980.0))).outstandingRent, 0.0)
        val partial = listOf(receipt(1, a, 300.0), receipt(2, b, 980.0))
        assertEquals(680.0, summary(listOf(a, b), partial).outstandingRent, 0.0)
        assertEquals(680.0, year(listOf(a, b), partial).missing, 0.0)
        assertEquals(680.0, summary(listOf(a), listOf(receipt(1, a, 300.0))).outstandingRent, 0.0)
        val mixed = listOf(receipt(1, a, 1960.0), receipt(2, b, 300.0), receipt(3, c, 500.0))
        assertEquals(680.0, summary(listOf(a, b, c), mixed).outstandingRent, 0.0)
        assertEquals(680.0, year(listOf(a, b, c), mixed).missing, 0.0)
        assertEquals(0.0, summary(emptyList(), emptyList()).outstandingRent, 0.0)
    }

    @Test fun multiplePartialPaymentsAndBankReceiptDedupStillCountOnce() {
        listOf(a, b).forEach { period(it) }
        val tx1 = BankTransaction("tx1", "account", "2026-10-03", amount = 300.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
        val tx2 = tx1.copy(transactionId = "tx2", amount = 680.0)
        fun assignment(tx: BankTransaction) = BankRentAssignment("a-${tx.transactionId}", tx.transactionId,
            property.propertyId, a.unitId, month.toString(), "tenant-a", tx.amount, BankSplitPaymentType.RENT,
            createdAt = "", updatedAt = "", source = BankRentAssignmentSource.USER_CONFIRMED)
        val r = receipt(1, a, 980.0)
        val link = BankReceiptLink("link", tx1.transactionId, r.id, r.internalId, 300.0)
        val links = listOf(link, link.copy(linkId = "link2", transactionId = tx2.transactionId, allocatedAmount = 680.0))
        val s = summary(listOf(a, b), listOf(r), listOf(assignment(tx1), assignment(tx2)), links, listOf(tx1, tx2))
        assertEquals(980.0, s.actualRent, 0.0)
        assertEquals(980.0, s.outstandingRent, 0.0)
    }

    @Test fun depositPrivateTransferAndReviewCannotReduceRentDebt() {
        period(a)
        val base = BankTransaction("tx", "account", "2026-10-03", amount = 980.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
        val assignment = BankRentAssignment("a", "tx", property.propertyId, a.unitId, month.toString(), "tenant-a", 980.0,
            BankSplitPaymentType.RENT, createdAt = "", updatedAt = "", source = BankRentAssignmentSource.USER_CONFIRMED)
        val cases = listOf(
            base to assignment.copy(paymentType = BankSplitPaymentType.DEPOSIT),
            base.copy(classification = BankTransactionClassification.PRIVATE_IGNORED) to assignment,
            base.copy(classification = BankTransactionClassification.TRANSFER) to assignment,
            base to assignment.copy(status = BankRentAssignmentStatus.REVIEW))
        cases.forEach { (tx, alloc) ->
            val s = summary(listOf(a), emptyList(), listOf(alloc), transactions = listOf(tx))
            assertEquals(0.0, s.actualRent, 0.0)
            assertEquals(980.0, s.outstandingRent, 0.0)
        }
        assertEquals(980.0, summary(listOf(a), listOf(receipt(1, a, 980.0).copy(unterkategorie = "Kaution"))).outstandingRent, 0.0)
    }

    @Test fun sameUnitNamesInOtherPropertiesNeverNetTheirBalances() {
        period(a)
        val other = property.copy(id = 92, propertyId = "other")
        period(b, pid = other.propertyId)
        val receipts = listOf(receipt(1, b, 1960.0, pid = other.propertyId))
        assertEquals(980.0, summary(listOf(a), receipts).outstandingRent, 0.0)
        assertEquals(0.0, summary(listOf(b), receipts, p = other).outstandingRent, 0.0)
        val portfolio = RentOverviewPresentation.year(context,
            listOf(RentPropertyUnits(property, listOf(a)), RentPropertyUnits(other, listOf(b))), receipts, 2026)
        assertEquals(980.0, portfolio.missing, 0.0)
    }

    @Test fun explicitRentMonthKeepsLateDecemberPaymentOutOfJanuary() {
        period(a, "2025-12-01", "2026-01-31")
        val tx = BankTransaction("late", "account", "2026-01-05", amount = 980.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
        val alloc = BankRentAssignment("late-a", tx.transactionId, property.propertyId, a.unitId, "2025-12", "tenant-a", 980.0,
            BankSplitPaymentType.RENT, createdAt = "", updatedAt = "", source = BankRentAssignmentSource.USER_CONFIRMED)
        assertEquals(0.0, summary(listOf(a), emptyList(), listOf(alloc), transactions = listOf(tx), m = YearMonth.of(2025, 12)).outstandingRent, 0.0)
        assertEquals(980.0, summary(listOf(a), emptyList(), listOf(alloc), transactions = listOf(tx), m = YearMonth.of(2026, 1)).outstandingRent, 0.0)
        val rows = RentTrackingLogic.year(context, property.propertyId, listOf(a), emptyList(), 2026, listOf(alloc), emptyList(), listOf(tx))
        assertEquals(0.0, rows.single().actual, 0.0)
    }

    @Test fun historicalVacancyAndDatedRentChangesKeepExistingExpectedAmounts() {
        period(a, "2026-04-01", "2026-09-30")
        val vacant = a.copy(status = "Leerstand")
        val row = year(listOf(vacant), emptyList()).rows.single()
        assertEquals(5880.0, row.expected, 0.0)
        assertEquals(5880.0, row.missing, 0.0)
        assertEquals(0.0, summary(listOf(vacant), emptyList()).expectedRent, 0.0)
        TenantHistoryStore.save(context, property.propertyId, b.unitId, b.name,
            listOf(TenantPeriod(9, b.name, b.mieter, "2026-04-01", "2026-12-31", 980.0, 0.0, 0.0,
                rentChanges = listOf(RentAmountChange("2026-10-01", 1000.0, 0.0, 0.0)))))
        assertEquals(8880.0, year(listOf(b), emptyList()).expected, 0.0)
    }

    @Test fun tenantChangeKeepsPreviousMonthsOverpaymentOutOfCurrentMonth() {
        TenantHistoryStore.save(context, property.propertyId, a.unitId, a.name, listOf(
            TenantPeriod(1, a.name, "Alt", "2026-09-01", "2026-09-30", 980.0, 0.0, 0.0),
            TenantPeriod(2, a.name, "Neu", "2026-10-01", "2026-10-31", 980.0, 0.0, 0.0)))
        val s = summary(listOf(a), listOf(receipt(1, a, 1960.0, "2026-09-03")))
        assertEquals(980.0, s.outstandingRent, 0.0)
        // Preserve the existing annual same-unit balance; this phase adds no tenancy-account model.
        val annual = RentTrackingLogic.year(context, property.propertyId, listOf(a), listOf(receipt(1, a, 1960.0, "2026-09-03")), 2026).single()
        assertEquals(980.0, annual.missing, 0.0)
        assertEquals(0.0, annual.outstanding, 0.0)
    }

    @Test fun negativeRentalCorrectionIsPreservedBeforeUnitClamp() {
        listOf(a, b).forEach { period(it) }
        val payments = listOf(receipt(1, a, 1960.0), receipt(2, b, -100.0))
        assertEquals(1860.0, summary(listOf(a, b), payments).actualRent, 0.0)
        assertEquals(1080.0, summary(listOf(a, b), payments).outstandingRent, 0.0)
        assertEquals(1080.0, year(listOf(a, b), payments).missing, 0.0)
    }
}
