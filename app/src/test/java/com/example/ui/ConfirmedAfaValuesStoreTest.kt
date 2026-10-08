package com.example.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfirmedAfaValuesStoreTest {
    @Test fun `verified values survive reload without mixing other property`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val propertyId = "afa-test-property"
        val otherId = "afa-test-other-property"
        val prefs = context.getSharedPreferences("afa_confirmed_values_prefs", Context.MODE_PRIVATE)
        prefs.edit().remove(propertyId).remove(otherId).commit()
        try {
            val confirmed = ConfirmedAfaValues(
                cutoffDate = "2025-12-31",
                cumulativeAfa = 40000.0,
                remainingBookValue = 360000.0,
                source = "Steuerberater Jahresabschluss 2025"
            )
            assertTrue(ConfirmedAfaValuesStore.write(context, propertyId, confirmed))
            assertEquals(confirmed, ConfirmedAfaValuesStore.read(context, propertyId))
            assertNull(ConfirmedAfaValuesStore.read(context, otherId))
        } finally {
            prefs.edit().remove(propertyId).remove(otherId).commit()
        }
    }
    @Test fun `corrupt or cross-property confirmed Afa values remain isolated`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("afa_confirmed_values_prefs", Context.MODE_PRIVATE)
        val good = ConfirmedAfaValues("2025-12-31", 40000.0, 360000.0, "Steuerbescheid")
        prefs.edit().remove("phase4-valid").remove("phase4-broken").commit()
        try {
            assertTrue(ConfirmedAfaValuesStore.write(context, "phase4-valid", good))
            val badPayloads = listOf(
                "broken JSON",
                """{"cutoffDate":"2025-02-31","cumulativeAfa":100,"remainingBookValue":400,"source":"Berater"}""",
                """{"cutoffDate":"2025-12-31","cumulativeAfa":-10,"remainingBookValue":400,"source":"Berater"}""",
                """{"cutoffDate":"2025-12-31","cumulativeAfa":100,"remainingBookValue":400,"source":""}"""
            )
            badPayloads.forEach { raw ->
                prefs.edit().putString("phase4-broken", raw).commit()
                assertNull(ConfirmedAfaValuesStore.read(context, "phase4-broken"))
                assertEquals(good, ConfirmedAfaValuesStore.read(context, "phase4-valid"))
            }
        } finally {
            prefs.edit().remove("phase4-valid").remove("phase4-broken").commit()
        }
    }

}