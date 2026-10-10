package com.example.ui

import com.example.data.*
import com.example.util.DatevMappingService
import org.junit.Assert.*
import org.junit.Test

class Phase6AReceiptIdentityTest {
    private fun unit(id: String, name: String) = WohneinheitStatus(name, name, "Leerstand", "", 0.0, 40.0, "", id)
    private val a = listOf(unit("qa-a-u01", "WE 01"), unit("qa-a-u02", "WE 02"))
    private val b = listOf(unit("qa-b-u01", "WE 01"))
    private fun receipt() = Receipt(1, "Testhandwerk", "2026-10-03", "", 119.0, "Werbungskosten",
        "Reparatur", "4801", "Synthetische Reparatur", wohneinheit = "WE 01", propertyId = "qa-a", unitId = "qa-a-u01", internalId = "phase6a-receipt")

    @Test fun explicitNewSelectionWinsOverValidOldIdAndStaleName() {
        assertEquals(ReceiptUnitSelection("qa-a-u02", "WE 02"), ReceiptUnitResolver.resolve("WE 01", "qa-a-u01", a, "qa-a-u02"))
    }
    @Test fun sameNameInOtherPropertyUsesItsOwnStableId() {
        assertEquals(ReceiptUnitSelection("qa-b-u01", "WE 01"), ReceiptUnitResolver.resolve("WE 01", "qa-a-u01", b, "qa-b-u01"))
        assertEquals(ReceiptUnitSelection(), ReceiptUnitResolver.resolve("WE 01", "qa-a-u01", b))
    }
    @Test fun generalSelectionClearsAnyPreviousUnit() {
        assertEquals(ReceiptUnitSelection(), ReceiptUnitResolver.resolve("WE 01", "qa-a-u01", a, ""))
        listOf("", "Gesamtobjekt / Allgemein", "ALLG", "GESAMT").forEach {
            assertEquals(ReceiptUnitSelection(), ReceiptUnitResolver.resolve(it, "qa-a-u01", a))
        }
    }
    @Test fun renameDerivesCurrentNameWithoutCreatingAnIdentity() {
        assertEquals(ReceiptUnitSelection("qa-a-u01", "Erdgeschoss links"),
            ReceiptUnitResolver.resolve("WE 01", "qa-a-u01", listOf(a.first().copy(name = "Erdgeschoss links", label = "EG"))))
    }
    @Test fun legacyNamesAreOnlyResolvedUniquelyInsideSelectedProperty() {
        assertEquals("qa-b-u01", ReceiptUnitResolver.resolve("WE 01", "", b).unitId)
        assertEquals(ReceiptUnitSelection(), ReceiptUnitResolver.resolve("WE 01", "", a + unit("duplicate", "WE 01")))
        assertEquals(ReceiptUnitSelection(), ReceiptUnitResolver.resolve("WE 02", "", b))
    }
    @Test fun contradictoryExistingIdentityIsReportedInsteadOfSilentlyReassigned() {
        assertNotNull(ReceiptUnitResolver.error(receipt().copy(wohneinheit = "WE 02"), a))
        assertNotNull(ReceiptUnitResolver.error(receipt().copy(propertyId = "qa-b"), b))
        assertNull(ReceiptUnitResolver.error(receipt(), a))
    }
    @Test fun savedSelectionMapsToTheSameDatevUnitAndKost2() {
        val selected = ReceiptUnitResolver.resolve("WE 02", "qa-a-u01", a, "qa-a-u02")
        val corrected = receipt().copy(unitId = selected.unitId, wohneinheit = selected.name)
        val profile = DatevProfile()
        val approved = DatevMappingService.confirmDatevPreview(corrected, DatevMappingService.buildDatevBookingRows(corrected, profile))!!
        val row = DatevMappingService.buildConfirmedDatevBookingRows(approved, profile).single()
        assertEquals("qa-a-u02", row.wohneinheitId)
        assertTrue(row.kost2.contains("WE02"))
        assertFalse(row.kost2.contains("WE01"))
        val general = corrected.copy(unitId = "", wohneinheit = "")
        val generalRow = DatevMappingService.buildDatevBookingRows(general, profile).single()
        assertEquals("", generalRow.wohneinheitId)
        assertTrue(generalRow.kost2.contains("ALLG"))
    }
}
