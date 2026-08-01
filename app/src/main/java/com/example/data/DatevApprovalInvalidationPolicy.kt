package com.example.data

import kotlin.math.roundToLong

/**
 * Invalidates a persisted DATEV approval when source data that affects accounting changes.
 *
 * Explicit approval itself is preserved: a transition from OFFEN to FREIGEGEBEN with unchanged
 * source data is not treated as an edit. Sync-only and Drive-only updates are also ignored.
 */
object DatevApprovalInvalidationPolicy {
    fun apply(persisted: Receipt?, candidate: Receipt): Receipt {
        if (persisted == null || persisted.freigabestatus != "FREIGEGEBEN") {
            return candidate
        }
        if (!hasRelevantChange(persisted, candidate)) {
            return candidate
        }
        return candidate.copy(
            allocationsJson = "",
            bookingProposalsJson = "",
            freigabestatus = "OFFEN",
            exportStatus = "EXPORTBEREIT",
            pruefstatus = "ZU_PRUEFEN",
            exportlaufId = ""
        )
    }

    fun hasRelevantChange(persisted: Receipt, candidate: Receipt): Boolean =
        amountCent(persisted.bruttobetrag) != amountCent(candidate.bruttobetrag) ||
            persisted.datum != candidate.datum ||
            persisted.hauptkategorie != candidate.hauptkategorie ||
            persisted.unterkategorie != candidate.unterkategorie ||
            persisted.kontoNr != candidate.kontoNr ||
            persisted.beschreibung != candidate.beschreibung ||
            persisted.isEigenleistungSanierung != candidate.isEigenleistungSanierung ||
            persisted.wohneinheit != candidate.wohneinheit ||
            persisted.mieter != candidate.mieter ||
            persisted.positionenJson != candidate.positionenJson

    private fun amountCent(value: Double): Long = (value * 100.0).roundToLong()
}
