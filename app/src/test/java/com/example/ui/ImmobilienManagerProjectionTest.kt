package com.example.ui

import com.example.data.ManagedDocument
import com.example.data.Loan
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class ImmobilienManagerProjectionTest {
    private val primary = PropertyMetadata(id = 1, propertyId = "property-1", name = "Bestand")
    private val second = PropertyMetadata(id = 2, propertyId = "property-2", name = "Neu", wohneinheiten = "WE 01")
    private val secondUnits = listOf(WohneinheitStatus("WE 01", "OG links", "Vermietet", "Testmieter", 950.0, 70.0, unitId = "unit-2"))

    @Test fun `existing property retains all legacy receipts`() {
        assertEquals(2, ImmobilienManagerProjection.receipts(primary, emptyList(), listOf(receipt(1, ""), receipt(2, "WE 01"))).size)
    }

    @Test fun `secondary property receipt and document filters use existing assignments`() {
        val receipts = ImmobilienManagerProjection.receipts(second, secondUnits, listOf(
            receipt(1, "WE 01", propertyId = "property-2"),
            receipt(2, "WE 99"),
            receipt(3, "WE 01", propertyId = "property-3")
        ))
        assertEquals(listOf(1), receipts.map { it.id })
        val documents = ImmobilienManagerProjection.documents(second, listOf(document("d1", "property-2"), document("d2", "property-1")))
        assertEquals(listOf("d1"), documents.map { it.documentId })
    }

    @Test fun `monthly property summary shows units rent and outstanding amount`() {
        val units = secondUnits + WohneinheitStatus("WE 02", "DG", "Leerstand", "", 700.0, 50.0, unitId = "unit-3")
        val summary = ImmobilienManagerProjection.summary(units, listOf(receipt(1, "WE 01", "2026-09-05", 600.0)), YearMonth.of(2026, 9))
        assertEquals(2, summary.unitCount)
        assertEquals(1, summary.rentedCount)
        assertEquals(1, summary.vacantCount)
        assertEquals(950.0, summary.expectedRent, 0.001)
        assertEquals(600.0, summary.actualRent, 0.001)
        assertEquals(350.0, summary.outstandingRent, 0.001)
        assertTrue(summary.outstandingRent > 0.0)
    }

    @Test fun `loans are visible only for their property`() {
        val loans = listOf(Loan(id = 1, bezeichnung = "Neu", propertyId = "property-2"), Loan(id = 2, bezeichnung = "Fremd", propertyId = "property-3"))
        assertEquals(listOf("Neu"), ImmobilienManagerProjection.loans(second, loans).map { it.bezeichnung })
    }

    private fun receipt(id: Int, unit: String, date: String = "2026-09-01", amount: Double = 100.0, propertyId: String = "property-1") = Receipt(
        id = id, aussteller = "Test", datum = date, uhrzeit = "", bruttobetrag = amount,
        hauptkategorie = "Miete, Nebenkosten & Kaution", unterkategorie = "Kaltmiete",
        kontoNr = "8100", beschreibung = "Miete", wohneinheit = unit, propertyId = propertyId, internalId = "receipt-$id"
    )

    private fun document(id: String, propertyId: String) = ManagedDocument(
        documentId = id, propertyId = propertyId, title = id
    )
}
