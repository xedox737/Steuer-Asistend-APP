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
    fun openReceiptIsExcludedFromProductiveDatevMapping() {
        val rows = DatevMappingService.mapReceiptToBookingRecords(
            receipt(id = 7, internalId = "open-receipt"),
            DatevProfile.createDefaultSkr03()
        )

        assertEquals(0, rows.size)
    }

    @Test
    fun explicitConfirmationPersistsAndExportsTheReviewedPreview() {
        val profile = DatevProfile.createDefaultSkr03()
        val openReceipt = receipt(id = 7, internalId = "confirmed-receipt")
        val preview = DatevMappingService.buildDatevBookingRows(openReceipt, profile)
        val confirmed = requireNotNull(DatevMappingService.confirmDatevPreview(openReceipt, preview))

        val exported = DatevMappingService.mapReceiptToBookingRecords(confirmed, profile)

        assertEquals("FREIGEGEBEN", confirmed.freigabestatus)
        assertEquals(preview.size, exported.size)
        assertEquals(10_000L, Math.round(exported.sumOf { it.bruttobetrag } * 100.0))
    }

    @Test
    fun changedAmountInvalidatesPreviouslyConfirmedAllocation() {
        val profile = DatevProfile.createDefaultSkr03()
        val openReceipt = receipt(id = 7, internalId = "changed-receipt")
        val confirmed = requireNotNull(
            DatevMappingService.confirmDatevPreview(
                openReceipt,
                DatevMappingService.buildDatevBookingRows(openReceipt, profile)
            )
        )

        val exported = DatevMappingService.mapReceiptToBookingRecords(
            confirmed.copy(bruttobetrag = 120.0),
            profile
        )

        assertEquals(0, exported.size)
    }

    @Test
    fun legacyExporterGuidAlsoSurvivesRoomIdChange() {
        val original = receipt(id = 7, internalId = "stable-receipt-id")
        val restored = receipt(id = 91, internalId = "stable-receipt-id")

        assertEquals(DatevExporter.getReceiptGuid(original), DatevExporter.getReceiptGuid(restored))
    }

    @Test(expected = IllegalArgumentException::class)
    fun legacyExporterBlocksDuplicateReceiptIdentity() {
        val original = receipt(id = 7, internalId = "duplicate-id")
        val duplicate = receipt(id = 91, internalId = "duplicate-id")

        DatevExporter.generateDocumentXml(listOf(original, duplicate), DatevConfig())
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

    @Test
    fun objectAndUnitCostCentersUseStableReceiptIdentityNotProfileName() {
        val profileA = DatevProfile.createDefaultSkr03().copy(profileName = "Kanzlei Alpha")
        val profileB = DatevProfile.createDefaultSkr03().copy(profileName = "Kanzlei Beta")
        val receipt = receipt(id = 7, internalId = "stable-receipt-id").copy(
            propertyId = "property-b",
            unitId = "unit-b-1",
            wohneinheit = "WE 1"
        )

        val rowA = DatevMappingService.buildDatevBookingRows(receipt, profileA).single()
        val rowB = DatevMappingService.buildDatevBookingRows(receipt, profileB).single()

        assertEquals("property-b", rowA.objektId)
        assertEquals("unit-b-1", rowA.wohneinheitId)
        assertEquals(rowA.kost1, rowB.kost1)
        assertEquals(rowA.kost2, rowB.kost2)
        assertNotEquals("Kanzlei Alpha".take(15), rowA.kost1)
    }

    @Test
    fun sameUnitLabelAcrossPropertiesGetsDifferentCostCenter() {
        val profile = DatevProfile.createDefaultSkr03()
        val first = receipt(1, "r-a").copy(propertyId = "property-a", unitId = "unit-a", wohneinheit = "WE 1")
        val second = receipt(2, "r-b").copy(propertyId = "property-b", unitId = "unit-b", wohneinheit = "WE 1")

        val a = DatevMappingService.buildDatevBookingRows(first, profile).single()
        val b = DatevMappingService.buildDatevBookingRows(second, profile).single()

        assertNotEquals(a.kost1, b.kost1)
        assertNotEquals(a.kost2, b.kost2)
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
