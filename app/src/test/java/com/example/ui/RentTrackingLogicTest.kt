package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.BankLinkStatus
import com.example.data.BankReceiptLink
import com.example.data.BankReconciliationStatus
import com.example.data.BankRentAssignment
import com.example.data.BankRentAssignmentSource
import com.example.data.BankRentAssignmentStatus
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.Receipt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.YearMonth

@RunWith(RobolectricTestRunner::class)
class RentTrackingLogicTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() = setUp()

    @Test
    fun `month with no tenancy is neutral and not arrears`() {
        val unit = WohneinheitStatus("OG", "OG", "Leerstand", "", 700.0, 60.0, unitId = "u1")
        val row = RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 3))
        assertEquals(RentPaymentStatus.NO_EXPECTATION, row.status)
        assertEquals(0.0, row.missing, 0.001)
    }

    @Test
    fun `year matrix uses same monthly expected and actual projection`() {
        val unit = WohneinheitStatus("OG", "OG", "Vermietet", "A", 1000.0, 60.0, "2026-01-01", "u1")
        PropertyUnitScopedData.setRentValues(context, "p1", unit, 200.0, 0.0)
        TenantHistoryStore.ensureCurrentPeriod(context, unit, 200.0, 0.0, "p1")
        val receipts = listOf(
            rent(1, "2026-01-03", 1200.0),
            rent(2, "2026-02-03", 600.0)
        )

        val january = RentTrackingLogic.month(context, "p1", unit, receipts, YearMonth.of(2026, 1))
        val february = RentTrackingLogic.month(context, "p1", unit, receipts, YearMonth.of(2026, 2))
        val year = RentTrackingLogic.year(context, "p1", listOf(unit), receipts, 2026).single()

        assertEquals(RentPaymentStatus.PAID, january.status)
        assertEquals(RentPaymentStatus.PARTIAL, february.status)
        assertEquals(january.expected, year.months[0].expected, 0.001)
        assertEquals(february.actual, year.months[1].actual, 0.001)
        assertEquals(11, year.suspiciousMonths)
    }


    @Test
    fun `stable receipt unit id prevents same-name cross-unit payment leakage`() {
        val unit = WohneinheitStatus("OG", "OG", "Vermietet", "A", 1000.0, 60.0, "2026-01-01", "unit-a")
        PropertyUnitScopedData.setRentValues(context, "p1", unit, 0.0, 0.0)
        TenantHistoryStore.ensureCurrentPeriod(context, unit, 0.0, 0.0, "p1")

        val wrongUnit = rent(1, "2026-01-03", 1000.0).copy(unitId = "unit-b")
        val correctUnit = rent(2, "2026-01-04", 400.0).copy(unitId = "unit-a")
        val legacy = rent(3, "2026-01-05", 100.0)

        val january = RentTrackingLogic.month(
            context,
            "p1",
            unit,
            listOf(wrongUnit, correctUnit, legacy),
            YearMonth.of(2026, 1)
        )

        assertEquals(500.0, january.actual, 0.001)
        assertEquals(RentPaymentStatus.PARTIAL, january.status)
    }

    @Test
    fun `dated rent increase keeps historical monthly expectations`() {
        val unit = WohneinheitStatus("OG", "OG", "Vermietet", "Mieter A", 760.0, 60.0, "2026-01-01", "u1")
        val period = TenantPeriod(
            id = 1L,
            unitName = "OG",
            tenantName = "Mieter A",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 220.0,
            sonstige = 0.0,
            rentChanges = listOf(RentAmountChange("2026-10-01", 850.0, 220.0, 0.0))
        )
        TenantHistoryStore.save(context, "p1", "u1", "OG", listOf(period))

        assertEquals(980.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 1)).expected, 0.001)
        assertEquals(980.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 9)).expected, 0.001)
        assertEquals(1070.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 10)).expected, 0.001)
        assertEquals(1070.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 11)).expected, 0.001)
    }

    @Test
    fun `multiple dated rent changes are resolved by effective month without overwriting`() {
        val unit = WohneinheitStatus("OG", "OG", "Vermietet", "Mieter A", 760.0, 60.0, "2026-01-01", "u1")
        val period = TenantPeriod(
            id = 2L,
            unitName = "OG",
            tenantName = "Mieter A",
            startDate = "2026-01-01",
            endDate = "",
            kaltmiete = 760.0,
            nebenkosten = 0.0,
            sonstige = 0.0,
            rentChanges = listOf(
                RentAmountChange("2026-07-01", 800.0, 0.0, 0.0),
                RentAmountChange("2026-10-01", 850.0, 0.0, 0.0)
            )
        )
        TenantHistoryStore.save(context, "p1", "u1", "OG", listOf(period))

        assertEquals(760.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 1)).expected, 0.001)
        assertEquals(800.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 7)).expected, 0.001)
        assertEquals(850.0, RentTrackingLogic.month(context, "p1", unit, emptyList(), YearMonth.of(2026, 10)).expected, 0.001)

        val reloaded = TenantHistoryStore.load(context, "p1", "u1", "OG").single()
        assertEquals(2, reloaded.rentChanges.size)
        assertEquals(760.0, reloaded.kaltmiete, 0.001)
    }

    @Test
    fun `confirmed bank split satisfies monthly rent without receipt`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transaction = bankTransaction("tx-split", 890.0, BankReconciliationStatus.MATCHED)
        val assignments = listOf(
            assignment("a-rent", "tx-split", "p1", "u1", 690.0, BankSplitPaymentType.RENT),
            assignment("a-nk", "tx-split", "p1", "u1", 200.0, BankSplitPaymentType.UTILITIES_PREPAYMENT)
        )

        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            assignments, emptyList(), listOf(transaction)
        )

        assertEquals(890.0, row.actual, 0.001)
        assertEquals(0.0, row.missing, 0.001)
        assertEquals(RentPaymentStatus.PAID, row.status)
    }

    @Test
    fun `confirmed bank partial payment reduces open amount`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transaction = bankTransaction("tx-partial", 500.0, BankReconciliationStatus.PARTIAL)
        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            listOf(assignment("a-partial", "tx-partial", "p1", "u1", 500.0, BankSplitPaymentType.RENT)),
            emptyList(), listOf(transaction)
        )

        assertEquals(500.0, row.actual, 0.001)
        assertEquals(390.0, row.missing, 0.001)
        assertEquals(RentPaymentStatus.PARTIAL, row.status)
    }

    @Test
    fun `multiple confirmed bank payments are summed for same month`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transactions = listOf(
            bankTransaction("tx-one", 500.0, BankReconciliationStatus.MATCHED),
            bankTransaction("tx-two", 390.0, BankReconciliationStatus.MATCHED)
        )
        val assignments = listOf(
            assignment("a-one", "tx-one", "p1", "u1", 500.0, BankSplitPaymentType.RENT),
            assignment("a-two", "tx-two", "p1", "u1", 390.0, BankSplitPaymentType.RENT)
        )

        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            assignments, emptyList(), transactions
        )

        assertEquals(890.0, row.actual, 0.001)
        assertEquals(RentPaymentStatus.PAID, row.status)
    }

    @Test
    fun `linked receipt and bank assignment are not double counted`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val receipt = rent(40, "2026-10-03", 890.0).copy(unitId = "u1")
        val transaction = bankTransaction("tx-linked", 890.0, BankReconciliationStatus.MATCHED)
        val link = BankReceiptLink(
            linkId = "link-1",
            transactionId = "tx-linked",
            receiptId = receipt.id,
            receiptInternalId = receipt.internalId,
            allocatedAmount = 890.0,
            status = BankLinkStatus.CONFIRMED,
            createdAt = "2026-10-03T12:00:00Z"
        )
        val assignment = assignment("a-linked", "tx-linked", "p1", "u1", 890.0, BankSplitPaymentType.RENT)

        val row = RentTrackingLogic.month(
            context, "p1", unit, listOf(receipt), YearMonth.of(2026, 10),
            listOf(assignment), listOf(link), listOf(transaction)
        )

        assertEquals(890.0, row.actual, 0.001)
        assertEquals(RentPaymentStatus.PAID, row.status)
    }

    @Test
    fun `bank payment never leaks across stable unit or property ids`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transactions = listOf(
            bankTransaction("tx-wrong-unit", 890.0, BankReconciliationStatus.MATCHED),
            bankTransaction("tx-wrong-property", 890.0, BankReconciliationStatus.MATCHED)
        )
        val assignments = listOf(
            assignment("wrong-unit", "tx-wrong-unit", "p1", "u2", 890.0, BankSplitPaymentType.RENT),
            assignment("wrong-property", "tx-wrong-property", "p2", "u1", 890.0, BankSplitPaymentType.RENT)
        )

        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            assignments, emptyList(), transactions
        )

        assertEquals(0.0, row.actual, 0.001)
        assertEquals(890.0, row.missing, 0.001)
        assertEquals(RentPaymentStatus.MISSING, row.status)
    }

    @Test
    fun `review bank assignment is not counted as actual rent`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transaction = bankTransaction("tx-review", 890.0, BankReconciliationStatus.MATCHED)
        val review = assignment("a-review", "tx-review", "p1", "u1", 890.0, BankSplitPaymentType.RENT)
            .copy(status = com.example.data.BankRentAssignmentStatus.REVIEW)

        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            listOf(review), emptyList(), listOf(transaction)
        )

        assertEquals(0.0, row.actual, 0.001)
        assertEquals(890.0, row.missing, 0.001)
    }

    @Test
    fun `assignment receipt reference prevents double count even without separate bank link`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val receipt = rent(41, "2026-10-03", 890.0).copy(unitId = "u1")
        val transaction = bankTransaction("tx-assignment-receipt", 890.0, BankReconciliationStatus.MATCHED)
        val assignment = assignment(
            "a-assignment-receipt", "tx-assignment-receipt", "p1", "u1", 890.0, BankSplitPaymentType.RENT
        ).copy(receiptId = receipt.id)

        val row = RentTrackingLogic.month(
            context, "p1", unit, listOf(receipt), YearMonth.of(2026, 10),
            listOf(assignment), emptyList(), listOf(transaction)
        )

        assertEquals(890.0, row.actual, 0.001)
        assertEquals(0.0, row.missing, 0.001)
    }

    @Test
    fun `confirmed overpayment is preserved while missing stays zero`() {
        val unit = rentalUnit()
        prepareExpected(unit, 690.0, 200.0)
        val transaction = bankTransaction("tx-over", 950.0, BankReconciliationStatus.MATCHED)
        val row = RentTrackingLogic.month(
            context, "p1", unit, emptyList(), YearMonth.of(2026, 10),
            listOf(assignment("a-over", "tx-over", "p1", "u1", 950.0, BankSplitPaymentType.RENT)),
            emptyList(), listOf(transaction)
        )

        assertEquals(950.0, row.actual, 0.001)
        assertEquals(0.0, row.missing, 0.001)
        assertEquals(RentPaymentStatus.PAID, row.status)
    }

    private fun rentalUnit() =
        WohneinheitStatus("OG", "OG", "Vermietet", "Mieter", 690.0, 60.0, "2026-01-01", "u1")

    private fun prepareExpected(unit: WohneinheitStatus, cold: Double, utilities: Double) {
        PropertyUnitScopedData.setRentValues(context, "p1", unit, utilities, 0.0)
        TenantHistoryStore.save(
            context, "p1", "u1", "OG",
            listOf(
                TenantPeriod(
                    id = 101,
                    unitName = "OG",
                    tenantName = "Mieter",
                    startDate = "2026-01-01",
                    endDate = "",
                    kaltmiete = cold,
                    nebenkosten = utilities,
                    sonstige = 0.0
                )
            )
        )
    }

    private fun bankTransaction(id: String, amount: Double, status: String) = BankTransaction(
        transactionId = id,
        accountId = "account",
        bookingDate = "2026-10-03",
        amount = amount,
        propertyId = "p1",
        unitId = "u1",
        reconciliationStatus = status
    )

    private fun assignment(
        id: String,
        transactionId: String,
        propertyId: String,
        unitId: String,
        amount: Double,
        paymentType: String
    ) = BankRentAssignment(
        assignmentId = id,
        transactionId = transactionId,
        propertyId = propertyId,
        unitId = unitId,
        rentMonth = "2026-10",
        tenantReference = "tenant-101",
        allocatedAmount = amount,
        paymentType = paymentType,
        status = BankRentAssignmentStatus.CONFIRMED,
        source = BankRentAssignmentSource.USER_CONFIRMED,
        createdAt = "2026-10-03T12:00:00Z",
        updatedAt = "2026-10-03T12:00:00Z"
    )


    private fun rent(id: Int, date: String, amount: Double) = Receipt(
        id = id,
        aussteller = "Mieter",
        datum = date,
        uhrzeit = "",
        bruttobetrag = amount,
        hauptkategorie = "Miete, Nebenkosten & Kaution",
        unterkategorie = "Kaltmiete",
        kontoNr = "8100",
        beschreibung = "Miete",
        wohneinheit = "OG",
        propertyId = "p1",
        internalId = "r$id"
    )
}
