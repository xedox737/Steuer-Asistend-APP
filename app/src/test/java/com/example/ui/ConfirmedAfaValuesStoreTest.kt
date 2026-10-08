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
}
