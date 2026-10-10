package com.example.data

import com.example.util.DatevMappingService
import com.example.util.DatevReceiptEligibility
import org.junit.Assert.*
import org.junit.Test

class Phase6AApprovalTest {
    private val profile = DatevProfile()
    private fun approved() = Receipt(8, "Testhandwerk", "2026-10-03", "", 119.0, "Werbungskosten",
        "Reparatur", "4801", "Synthetische Reparatur", propertyId = "a", unitId = "a-unit", wohneinheit = "WE 01", internalId = "phase6a-approval")
        .let { DatevMappingService.confirmDatevPreview(it, DatevMappingService.buildDatevBookingRows(it, profile))!! }
    private fun checkInvalidated(candidate: Receipt) {
        val result = DatevApprovalInvalidationPolicy.apply(approved(), candidate)
        assertEquals("OFFEN", result.freigabestatus)
        assertEquals("ZU_PRUEFEN", result.pruefstatus)
        assertEquals("", result.allocationsJson)
        assertEquals("", result.bookingProposalsJson)
        assertFalse(DatevReceiptEligibility.isAccountingApproved(result))
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(result, profile).isEmpty())
    }
    @Test fun propertyChangeInvalidatesGeneralReceiptEvenWithSameLabel() {
        val old = approved().copy(unitId = "", wohneinheit = "")
        val result = DatevApprovalInvalidationPolicy.apply(old, old.copy(propertyId = "b"))
        assertEquals("OFFEN", result.freigabestatus)
        assertEquals("ZU_PRUEFEN", result.pruefstatus)
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(result, profile).isEmpty())
    }
    @Test fun stableUnitChangeInvalidatesEvenWithSameVisibleName() = checkInvalidated(approved().copy(unitId = "a-other"))
    @Test fun amountDateCategoryAndDescriptionRetainExistingInvalidation() {
        val old = approved()
        listOf(old.copy(bruttobetrag = 120.0), old.copy(datum = "2026-10-04"), old.copy(unterkategorie = "Material"),
            old.copy(beschreibung = "Geänderte Beschreibung")).forEach(::checkInvalidated)
    }
    @Test fun accountCounterAccountAndTaxProposalChangesRequireReview() {
        val old = approved()
        val proposals = AccountingApprovalJson.decodeBookingProposals(old.bookingProposalsJson)
        listOf(proposals.map { it.copy(konto = "4802") }, proposals.map { it.copy(gegenkonto = "10000") },
            proposals.map { it.copy(buSchluessel = "9") }).forEach {
            checkInvalidated(old.copy(bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(it)))
        }
    }
    @Test fun exportedEditUsesExistingReviewResetAndLeavesPreviousReceiptValueIntact() {
        val old = approved().copy(exportStatus = "EXPORTIERT", exportlaufId = "historic-export")
        val result = DatevApprovalInvalidationPolicy.apply(old, old.copy(propertyId = "b"))
        assertEquals("OFFEN", result.freigabestatus)
        assertEquals("EXPORTBEREIT", result.exportStatus)
        assertEquals("", result.exportlaufId)
        assertEquals("historic-export", old.exportlaufId)
    }
    @Test fun explicitReapprovalIsRequiredAndThenRestoresExportEligibility() {
        val changed = DatevApprovalInvalidationPolicy.apply(approved(), approved().copy(propertyId = "b"))
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(changed, profile).isEmpty())
        val confirmed = DatevMappingService.confirmDatevPreview(changed, DatevMappingService.buildDatevBookingRows(changed, profile))!!
        val saved = DatevApprovalInvalidationPolicy.apply(changed, confirmed)
        assertTrue(DatevReceiptEligibility.isAccountingApproved(saved))
        assertEquals("b", DatevMappingService.buildConfirmedDatevBookingRows(saved, profile).single().objektId)
    }
    @Test fun syncOnlyChangeKeepsApproval() {
        val old = approved()
        assertEquals("FREIGEGEBEN", DatevApprovalInvalidationPolicy.apply(old, old.copy(syncStatus = "SYNCED")).freigabestatus)
    }
}
