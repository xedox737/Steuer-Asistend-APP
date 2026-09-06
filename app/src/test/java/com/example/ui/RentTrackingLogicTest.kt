package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
