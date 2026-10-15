package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.PropertyMetadata
import com.example.data.TaxPropertyCalculator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase9DCutoffContractTest {
    private val property = PropertyMetadata(propertyId = "9d-cutoff", baujahr = 1954,
        gebaeudewert = 888_000.0, gesamtKaufpreis = 888_000.0, uebergangNutzenLasten = "2020-01-01")
    private val summary get() = TaxPropertyCalculator.calculate(property, emptyList())
    private fun confirmed(date: String) = ConfirmedAfaValues(date, 30_000.0, 100_000.0, "Steuerberater")

    @Test fun juneEndIncludesSixMonthsInTheSameYear() {
        val rows = afaForecast(summary, confirmed("2026-06-30"))
        assertEquals(2026, rows.first().year)
        assertEquals(6, rows.first().months)
        assertEquals(8_880.0, rows.first().amount, .001)
        assertEquals(17_760.0, rows[1].amount, .001)
    }

    @Test fun decemberEndBeginsInTheFollowingYearWithoutDoubleCounting() {
        val rows = afaForecast(summary, confirmed("2025-12-31"))
        assertEquals(2026, rows.first().year)
        assertEquals(12, rows.first().months)
        assertEquals(17_760.0, rows.first().amount, .001)
        assertEquals(10, rows.map { it.year }.distinct().size)
    }

    @Test fun underMonthlyDatesUseTheExplicitlyAgreedClosedMonthConvention() {
        val midJune = afaForecast(summary, confirmed("2026-06-15"))
        assertEquals(afaForecast(summary, confirmed("2026-06-30")), midJune)
        val januaryFirst = afaForecast(summary, confirmed("2026-01-01"))
        assertEquals(2026, januaryFirst.first().year)
        assertEquals(11, januaryFirst.first().months)
        assertEquals(16_280.0, januaryFirst.first().amount, .001)
    }

    @Test fun leapDayLeavesTheExistingMonthlyConventionUnchanged() {
        val row = afaForecast(summary, confirmed("2024-02-29")).first()
        assertEquals(2024, row.year)
        assertEquals(10, row.months)
        assertEquals(14_800.0, row.amount, .001)
    }

    @Test fun noConfirmationKeepsTheAcquisitionMonthAndMissingStartStaysOpen() {
        val tax = TaxPropertyCalculator.calculate(property.copy(uebergangNutzenLasten = "2026-04-15"), emptyList())
        assertEquals(tax.firstYearAfa, afaForecast(tax, null).first().amount, .001)
        val missing = TaxPropertyCalculator.calculate(property.copy(uebergangNutzenLasten = ""), emptyList())
        assertTrue(afaForecast(missing, confirmed("2026-06-30")).isEmpty())
        assertTrue(afaForecast(summary, confirmed("kaputt")).isEmpty())
    }

    @Test fun storeRawValuesAndExternalConfirmationRemainUnchangedDuringForecast() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("afa_confirmed_values_prefs", 0)
        val other = property.propertyId + "-other"
        try {
            val value = confirmed("2026-06-30")
            assertTrue(ConfirmedAfaValuesStore.write(context, property.propertyId, value))
            val raw = prefs.getString(property.propertyId, null)
            assertEquals(value, ConfirmedAfaValuesStore.read(context, property.propertyId))
            assertNull(ConfirmedAfaValuesStore.read(context, other))
            repeat(2) { assertEquals(8_880.0, afaForecast(summary, ConfirmedAfaValuesStore.read(context, property.propertyId)).first().amount, .001) }
            assertEquals(raw, prefs.getString(property.propertyId, null))
            assertEquals(value, ConfirmedAfaValuesStore.read(context, property.propertyId))
        } finally {
            prefs.edit().remove(property.propertyId).remove(other).commit()
        }
    }
}
