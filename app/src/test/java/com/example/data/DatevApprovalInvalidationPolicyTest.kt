package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DatevApprovalInvalidationPolicyTest {
    @Test
    fun changedAmountInvalidatesAndClearsPersistedApproval() {
        val persisted = approvedReceipt()
        val result = DatevApprovalInvalidationPolicy.apply(
            persisted,
            persisted.copy(bruttobetrag = 120.0)
        )

        assertEquals("OFFEN", result.freigabestatus)
        assertEquals("", result.allocationsJson)
        assertEquals("", result.bookingProposalsJson)
        assertEquals("ZU_PRUEFEN", result.pruefstatus)
        assertEquals("EXPORTBEREIT", result.exportStatus)
        assertEquals("", result.exportlaufId)
    }

    @Test
    fun accountingSourceChangesInvalidateApproval() {
        val persisted = approvedReceipt()
        val changed = listOf(
            persisted.copy(datum = "2026-08-02"),
            persisted.copy(hauptkategorie = "Andere Kategorie"),
            persisted.copy(unterkategorie = "Andere Unterkategorie"),
            persisted.copy(kontoNr = "4802"),
            persisted.copy(beschreibung = "Geänderte Beschreibung"),
            persisted.copy(isEigenleistungSanierung = true),
            persisted.copy(wohneinheit = "WE 2"),
            persisted.copy(mieter = "Andere Person"),
            persisted.copy(positionenJson = """[{"id":"changed"}]""")
        )

        assertTrue(changed.all {
            DatevApprovalInvalidationPolicy.apply(persisted, it).freigabestatus == "OFFEN"
        })
    }

    @Test
    fun syncOnlyChangesPreserveApproval() {
        val persisted = approvedReceipt()
        val result = DatevApprovalInvalidationPolicy.apply(
            persisted,
            persisted.copy(syncStatus = "SYNCED", lastSyncedAt = "2026-08-01T12:00:00")
        )

        assertEquals("FREIGEGEBEN", result.freigabestatus)
        assertEquals(persisted.allocationsJson, result.allocationsJson)
        assertEquals(persisted.bookingProposalsJson, result.bookingProposalsJson)
    }

    @Test
    fun explicitFirstApprovalIsNotInvalidated() {
        val open = approvedReceipt().copy(
            allocationsJson = "",
            bookingProposalsJson = "",
            freigabestatus = "OFFEN"
        )
        val confirmed = open.copy(
            allocationsJson = "[confirmed]",
            bookingProposalsJson = "[confirmed]",
            freigabestatus = "FREIGEGEBEN"
        )

        assertEquals(
            "FREIGEGEBEN",
            DatevApprovalInvalidationPolicy.apply(open, confirmed).freigabestatus
        )
    }

    private fun approvedReceipt() = Receipt(
        id = 7,
        aussteller = "Handwerker GmbH",
        datum = "2026-08-01",
        uhrzeit = "",
        bruttobetrag = 100.0,
        hauptkategorie = "Werbungskosten",
        unterkategorie = "Reparatur",
        kontoNr = "4801",
        beschreibung = "Bestätigte Reparatur",
        allocationsJson = "[allocation]",
        bookingProposalsJson = "[proposal]",
        freigabestatus = "FREIGEGEBEN",
        pruefstatus = "GEPRUEFT",
        exportStatus = "EXPORTIERT",
        exportlaufId = "export-1",
        internalId = "receipt-approval-1"
    )
}
