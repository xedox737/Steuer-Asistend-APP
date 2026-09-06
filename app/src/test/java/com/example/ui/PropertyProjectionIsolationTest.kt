package com.example.ui

import com.example.data.PropertyMetadata
import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Test

class PropertyProjectionIsolationTest {
    @Test
    fun `legacy receipt with identical unit name never leaks into second property`() {
        val property = PropertyMetadata(id = 2, propertyId = "property-2", wohneinheiten = "OG links")
        val unit = WohneinheitStatus("OG links", "OG links", "Vermietet", "B", 900.0, 70.0, unitId = "unit-2")
        val legacy = receipt(1, "property-1")
        val assigned = receipt(2, "property-2")

        val result = ImmobilienManagerProjection.receipts(property, listOf(unit), listOf(legacy, assigned))

        assertEquals(listOf(2), result.map { it.id })
    }

    private fun receipt(id: Int, propertyId: String) = Receipt(
        id = id,
        aussteller = "Test",
        datum = "2026-09-01",
        bruttobetrag = 900.0,
        hauptkategorie = "Miete, Nebenkosten & Kaution",
        unterkategorie = "Kaltmiete",
        kontoNr = "8100",
        beschreibung = "Miete",
        wohneinheit = "OG links",
        propertyId = propertyId,
        internalId = "receipt-$id"
    )
}
