package com.example.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class FilelessLogbookContractTest {
    private val receipt = Receipt(id=61, internalId="expense-61", propertyId="p1", aussteller="Fahrtkosten", datum="2026-09-10",
        uhrzeit="12:00", bruttobetrag=6.60, hauptkategorie="Sonstige Ausgaben", unterkategorie="Fahrtkosten", kontoNr="4670", beschreibung="Confirmed trip")
    private val trip = LogbookTrip(id=1,date="2026-09-10",purpose="Inspection",startAddress="A",destinationAddress="B",
        taxDistanceKm=22.0,kilometerSource="MANUELL",manuallyConfirmed=true,expenseReceiptId=61,propertyId="p1",bookingKey="trip-1",createdAt="",updatedAt="")

    @Test fun onlyActualConfirmedExpenseBindingCreatesAnExemption() {
        val proof=FilelessLogbookContract.create(receipt,trip)!!
        assertEquals("expense-61",FilelessLogbookContract.decode(proof).internalId)
        assertEquals(660L,FilelessLogbookContract.decode(proof).cents)
        assertEquals(proof,FilelessLogbookContract.read(JSONObject().put("filelessLogbookEvidence",JSONObject(proof))))
    }

    @Test fun ordinaryReceiptNeverGetsAttachmentExemption() {
        assertNull(FilelessLogbookContract.create(receipt.copy(unterkategorie="Reparatur"),trip))
        assertNull(FilelessLogbookContract.create(receipt.copy(kontoNr="4800"),trip))
    }

    @Test fun existingOrMissingExplicitOriginalIsNotConvertedToFilelessReceipt() {
        assertNull(FilelessLogbookContract.create(receipt.copy(imageUrl="/missing/original.pdf"),trip))
        assertNull(FilelessLogbookContract.create(receipt.copy(driveFileId="existing-original"),trip))
    }

    @Test fun unconfirmedCancelledAiOnlyAndForeignTripsAreRejected() {
        for (bad in listOf(trip.copy(manuallyConfirmed=false),trip.copy(cancelledAt="2026-10-01"),
            trip.copy(kilometerSource="KI_GESCHAETZT"),trip.copy(expenseReceiptId=62),trip.copy(propertyId="p2"),
            trip.copy(date="2026-09-11"),trip.copy(taxDistanceKm=23.0),trip.copy(bookingKey=""))) {
            assertNull(bad.toString(),FilelessLogbookContract.create(receipt,bad))
        }
    }

    @Test fun malformedOrTamperedEvidenceCannotBecomeAnExemption() {
        val proof=JSONObject(FilelessLogbookContract.create(receipt,trip)!!)
        for (bad in listOf(JSONObject(proof.toString()).put("tripId",0),JSONObject(proof.toString()).put("distanceKm",-1),
            JSONObject(proof.toString()).put("manuallyConfirmed",false),JSONObject(proof.toString()).put("version",2))) {
            assertThrows(Exception::class.java) { FilelessLogbookContract.decode(bad.toString()) }
        }
    }
}
