package com.example.util

import com.example.data.DatevProfile
import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DatevStableIdentityTest {
    @Test
    fun restoredRoomRowKeepsDatevReferencesWhenInternalIdStaysTheSame() {
        val profile = DatevProfile.createDefaultSkr03()
        val original = receipt(id = 7, internalId = "stable-receipt-id")
        val restored = receipt(id = 91, internalId = "stable-receipt-id")

        val originalRow = DatevMappingService.buildDatevBookingRows(original, profile).single()
        val restoredRow = DatevMappingService.buildDatevBookingRows(restored, profile).single()

        assertEquals(originalRow.belegfeld1, restoredRow.belegfeld1)
        assertEquals(originalRow.bookingId, restoredRow.bookingId)
    }

    @Test
    fun differentInternalIdsReceiveDifferentDatevReferences() {
        val profile = DatevProfile.createDefaultSkr03()
        val first = DatevMappingService.buildDatevBookingRows(
            receipt(id = 7, internalId = "receipt-one"),
            profile
        ).single()
        val second = DatevMappingService.buildDatevBookingRows(
            receipt(id = 7, internalId = "receipt-two"),
            profile
        ).single()

        assertNotEquals(first.belegfeld1, second.belegfeld1)
        assertNotEquals(first.bookingId, second.bookingId)
    }

    private fun receipt(id: Int, internalId: String) = Receipt(
        id = id,
        aussteller = "Testlieferant",
        datum = "2026-08-01",
        uhrzeit = "10:00",
        bruttobetrag = 100.00,
        hauptkategorie = "Sonstige Ausgaben",
        unterkategorie = "Test",
        kontoNr = "4801",
        beschreibung = "Bestätigter Testbeleg",
        internalId = internalId,
        displayId = "BLG-2026-000001"
    )
}
