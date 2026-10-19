package com.example.data

import com.example.util.DatevMappingService
import com.example.util.DatevReceiptEligibility
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase10DApprovalRepresentationTest : Phase10CRestoreFixture() {
    @Test fun emptyStringRoundTripRemainsExact() = emptyRoundTrip("")
    @Test fun explicitEmptyArrayRoundTripRemainsExact() = emptyRoundTrip("[]")

    private fun emptyRoundTrip(raw: String) = runTest {
        seedProperty()
        val before = receipt(1).copy(allocationsJson = raw, bookingProposalsJson = raw)
        repository.insert(before)
        backup()
        restore(RestoreMode.REPLACE_FULL)
        val restored = repository.getReceiptById(1)!!
        assertEquals(raw, restored.allocationsJson)
        assertEquals(raw, restored.bookingProposalsJson)
        assertEquals(DatevReceiptEligibility.issues(before), DatevReceiptEligibility.issues(restored))
        assertEquals(AccountingApprovalJson.decodeAllocations(before.allocationsJson),
            AccountingApprovalJson.decodeAllocations(restored.allocationsJson))
        assertEquals(AccountingApprovalJson.decodeBookingProposals(before.bookingProposalsJson),
            AccountingApprovalJson.decodeBookingProposals(restored.bookingProposalsJson))
        assertEquals(DatevMappingService.buildConfirmedDatevBookingRows(before, DatevProfile()),
            DatevMappingService.buildConfirmedDatevBookingRows(restored, DatevProfile()))
    }

    @Test fun confirmedRepresentationMustNotLoseApprovalOrExportLinkOnUnchangedResubmission() = runTest {
        seedProperty()
        val profile = DatevProfile()
        val approved = DatevMappingService.confirmDatevPreview(receipt(1),
            DatevMappingService.buildDatevBookingRows(receipt(1), profile))!!
        val original = approved.copy(
            allocationsJson = " \n" + approved.allocationsJson,
            bookingProposalsJson = " \n" + approved.bookingProposalsJson,
            exportStatus = "EXPORTIERT", exportlaufId = "historic-export-10d")
        assertTrue(DatevReceiptEligibility.isAccountingApproved(original))
        repository.insert(original)
        backup()
        val snapshot = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(snapshot.errors.toString(), snapshot.errors.none { it.isBlocking })
        val restored = with(drive) { snapshot.receipts.single().toLocalReceipt("meta-receipt-1") }
        // Simulates the accounting editor resubmitting the same previously confirmed
        // allocations; all other fields come from the current persisted receipt.
        val unchangedAccounting = restored.copy(allocationsJson = original.allocationsJson,
            bookingProposalsJson = original.bookingProposalsJson)
        val result = DatevApprovalInvalidationPolicy.apply(restored, unchangedAccounting)
        assertEquals("Unchanged confirmed values must retain their audit link", original.exportlaufId, result.exportlaufId)
        assertEquals(original.freigabestatus, result.freigabestatus)
        assertEquals(original.exportStatus, result.exportStatus)
        assertEquals(original.allocationsJson, restored.allocationsJson)
        assertEquals(original.bookingProposalsJson, restored.bookingProposalsJson)
    }

    @Test fun oldV2WithoutRawRepresentationsStillReadsTypedValuesAndContradictionsAreRejected() = runTest {
        seedProperty()
        val approved = DatevMappingService.confirmDatevPreview(receipt(1),
            DatevMappingService.buildDatevBookingRows(receipt(1), DatevProfile()))!!
        repository.insert(approved)
        backup()
        val snapshot = drive.buildRestoreSnapshot("test-token", config)
        val json = org.json.JSONObject(with(drive) { snapshot.receipts.single().toJson() })
        json.remove("rawAllocationsJson"); json.remove("rawBookingProposalsJson")
        val legacy = with(drive) { parsePersistedReceipt(json.toString()).toLocalReceipt("legacy") }
        assertEquals(AccountingApprovalJson.decodeAllocations(approved.allocationsJson),
            AccountingApprovalJson.decodeAllocations(legacy.allocationsJson))
        assertEquals(AccountingApprovalJson.decodeBookingProposals(approved.bookingProposalsJson),
            AccountingApprovalJson.decodeBookingProposals(legacy.bookingProposalsJson))
        json.put("rawAllocationsJson", "[]")
        assertThrows(IllegalArgumentException::class.java) { drive.parsePersistedReceipt(json.toString()) }
        json.remove("rawAllocationsJson"); json.put("rawBookingProposalsJson", "[]")
        assertThrows(IllegalArgumentException::class.java) { drive.parsePersistedReceipt(json.toString()) }
    }
}
