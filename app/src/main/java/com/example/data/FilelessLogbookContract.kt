package com.example.data

import org.json.JSONObject
import java.time.LocalDate

/** Explicit attachment exemption for an actually booked, confirmed trip, never a generic skip flag. */
internal object FilelessLogbookContract {
    data class Evidence(val tripId: Long, val expenseReceiptId: Int, val internalId: String,
        val propertyId: String, val date: String, val distanceKm: Double, val source: String, val bookingKey: String, val account: String) {
        fun json(): JSONObject = JSONObject().put("version", 1).put("tripId", tripId)
            .put("expenseReceiptId", expenseReceiptId).put("internalId", internalId).put("propertyId", propertyId)
            .put("date", date).put("distanceKm", distanceKm).put("source", source).put("bookingKey", bookingKey)
            .put("account", account).put("manuallyConfirmed", true).put("cancelledAt", "")
        val cents: Long get() = ReceiptMoney.toCent(distanceKm * 0.30)
    }

    fun decode(value: String): Evidence {
        val j = JSONObject(value)
        require(j.getInt("version") == 1 && j.getBoolean("manuallyConfirmed") && j.getString("cancelledAt").isEmpty())
        val id = j.backupLong("expenseReceiptId")
        require(id in 1..Int.MAX_VALUE.toLong())
        return Evidence(j.backupLong("tripId"), id.toInt(), j.getString("internalId"), j.getString("propertyId"),
            j.getString("date"), j.backupDouble("distanceKm"), j.getString("source"), j.getString("bookingKey"), j.optString("account", "4670")).also {
            require(it.tripId > 0 && it.internalId.isNotBlank() && it.propertyId.isNotBlank() && it.bookingKey.isNotBlank() && it.account.isNotBlank())
            LocalDate.parse(it.date)
            require(it.distanceKm.isFinite() && it.distanceKm >= 0)
            require(KilometerSource.valueOf(it.source) != KilometerSource.KI_GESCHAETZT)
            it.cents // enforce the same amount boundary as an ordinary receipt
        }
    }

    fun read(json: JSONObject): String? {
        if (!json.has("filelessLogbookEvidence") || json.isNull("filelessLogbookEvidence")) return null
        return decode(json.getJSONObject("filelessLogbookEvidence").toString()).json().toString()
    }

    fun create(receipt: Receipt, trip: LogbookTrip): String? = runCatching {
        require(receipt.id > 0 && trip.expenseReceiptId == receipt.id && trip.manuallyConfirmed && trip.cancelledAt.isBlank())
        require(receipt.imageUrl.isBlank() && receipt.driveFileId.isNullOrBlank())
        require(receipt.unterkategorie == "Fahrtkosten" && receipt.kontoNr.isNotBlank())
        require(receipt.propertyId == trip.propertyId && receipt.datum == trip.date)
        val e = decode(Evidence(trip.id, receipt.id, receipt.internalId, trip.propertyId, trip.date,
            trip.taxDistanceKm, trip.kilometerSource, trip.bookingKey, receipt.kontoNr).json().toString())
        require(e.cents == ReceiptMoney.toCent(receipt.bruttobetrag))
        e.json().toString()
    }.getOrNull()

    fun validIndex(entry: ReceiptIndexEntry): Boolean = runCatching {
        val e = decode(requireNotNull(entry.filelessLogbookEvidence))
        require(entry.mainDriveFileId.isBlank() && entry.internalId == e.internalId && entry.datum == e.date &&
            entry.bruttobetragCent == e.cents && entry.unterkategorie == "Fahrtkosten")
    }.isSuccess

    fun validateMetadata(receipt: PersistedReceipt) {
        val value = receipt.filelessLogbookEvidence ?: return
        val e = decode(value)
        require(receipt.receiptId == e.expenseReceiptId && receipt.internalId == e.internalId && receipt.propertyId == e.propertyId &&
            receipt.datum == e.date && receipt.bruttobetragCent == e.cents && receipt.kontoNr == e.account && receipt.unterkategorie == "Fahrtkosten") {
            "Dateiloser Fahrtbeleg widerspricht seiner bestätigten Fahrt."
        }
        require(receipt.driveFileId.isBlank() && receipt.documents.isEmpty() && receipt.originalDocuments.isEmpty()) {
            "Dateiloser Fahrtbeleg darf keine Originaldateien ausblenden."
        }
    }

    fun validatePair(entry: ReceiptIndexEntry, receipt: PersistedReceipt) {
        validateMetadata(receipt)
        require(entry.filelessLogbookEvidence == receipt.filelessLogbookEvidence) { "Fahrtnachweis in Index und Metadaten widersprüchlich." }
        if (entry.filelessLogbookEvidence != null) require(validIndex(entry)) { "Ungültiger dateiloser Indexeintrag." }
    }

    fun validateSupplemental(receipts: List<PersistedReceipt>, payload: JSONObject) {
        val trips = payload.getJSONArray("logbookTrips")
        val seenTrips = mutableSetOf<Long>()
        for (receipt in receipts) {
            val proof = receipt.filelessLogbookEvidence ?: continue
            val e = decode(proof)
            require(seenTrips.add(e.tripId)) { "Fahrt ist mehrfach als dateiloser Beleg gesichert." }
            val matches = (0 until trips.length()).map { trips.getJSONObject(it) }.filter { it.backupLong("id") == e.tripId }
            val trip = matches.single()
            require(trip.backupLong("expenseReceiptId") == e.expenseReceiptId.toLong() && trip.getString("propertyId") == e.propertyId &&
                trip.getString("date") == e.date && trip.backupDouble("taxDistanceKm") == e.distanceKm &&
                trip.getString("kilometerSource") == e.source && trip.getString("bookingKey") == e.bookingKey &&
                trip.getBoolean("manuallyConfirmed") && trip.optString("cancelledAt").isBlank()) {
                "Gesicherter Fahrtbeleg widerspricht dem Fahrtenbuch."
            }
        }
    }
}
