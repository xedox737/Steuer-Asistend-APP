package com.example.data

import androidx.room.withTransaction
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** All local booking writes commit together. Retries share a stable booking key. */
class LogbookBookingStore(private val database: AppDatabase, private val repository: ReceiptRepository) {
    suspend fun saveLogbookTrip(
        originalReceipt: Receipt,
        purpose: String,
        startAddress: String,
        destinationAddress: String,
        stops: List<com.example.data.TripStop>,
        routeMode: com.example.data.TripRouteMode,
        sameReturnRoute: Boolean,
        evidence: com.example.data.DistanceEvidence,
        routeResult: com.example.data.RouteDistanceResult? = null,
        standardRouteId: Long? = null,
        correctionReason: String = "",
        correctionNote: String = "",
        note: String = "",
        tripDate: String = originalReceipt.datum,
        propertyId: String = originalReceipt.propertyId,
        bookingKey: String = ""
    ): Result<Long> = runCatching { database.withTransaction {
        val effectiveKey = bookingKey.ifBlank { if (originalReceipt.id > 0) "receipt:${originalReceipt.id}" else "" }
        if (effectiveKey.isNotBlank()) database.logbookDao().getByBookingKey(effectiveKey)?.let { return@withTransaction it.id }
        if (originalReceipt.id > 0) {
            database.logbookDao().getBySourceReceiptId(originalReceipt.id)?.let {
                require(it.cancelledAt.isBlank()) { "Die verknüpfte Fahrt wurde storniert. Bitte eine neue manuelle Fahrt anlegen." }
                return@withTransaction it.id
            }
            val sourceReceipt = requireNotNull(repository.getReceiptById(originalReceipt.id)) { "Der verknüpfte Beleg fehlt." }
            require(sourceReceipt.deletionStatus == "ACTIVE") { "Der verknüpfte Beleg wurde gelöscht." }
            require(sourceReceipt.propertyId == propertyId) { "Beleg und Fahrt müssen derselben Immobilie zugeordnet sein." }
        }
        require(purpose.isNotBlank()) { "Fahrtzweck erforderlich." }
        java.time.LocalDate.parse(tripDate)
        require(propertyId.isNotBlank()) { "Immobilie erforderlich." }
        require(evidence.manuallyConfirmed) { "Route, Fahrtzweck und Kilometer müssen vor dem Einbuchen bestätigt werden." }
        val normalized = com.example.data.TripRouteNormalizer.normalize(
            com.example.data.RouteDistanceRequest(stops, routeMode, sameReturnRoute)
        )
        val checkedEvidence = evidence.copy(correctionReason = correctionReason)
        val decision = com.example.data.LogbookDistancePolicy.decide(checkedEvidence)
        val distance = requireNotNull(decision.taxDistanceKm) {
            "Die steuerliche Kilometerzahl muss durch Route, GPS, Tacho, Standardstrecke oder manuell bestätigt werden."
        }
        val source = requireNotNull(decision.source)
        require(source != com.example.data.KilometerSource.KI_GESCHAETZT) {
            "Eine reine KI-Schätzung darf nicht steuerlich eingebucht werden."
        }
        require(!decision.correctionReasonRequired || correctionReason.isNotBlank()) {
            "Für die deutlich abweichende manuelle Strecke ist ein Korrekturgrund erforderlich."
        }
        require(correctionReason != "Sonstiges" || correctionNote.isNotBlank()) {
            "Für den Korrekturgrund Sonstiges ist eine kurze Beschreibung erforderlich."
        }
        val now = java.time.Instant.now().toString()
        val expenseId = repository.insert(
            Receipt(
                aussteller = "Fahrtkosten: ${originalReceipt.aussteller}",
                datum = tripDate,
                uhrzeit = originalReceipt.uhrzeit,
                bruttobetrag = distance * 0.30,
                hauptkategorie = "Sonstige Ausgaben",
                unterkategorie = "Fahrtkosten",
                kontoNr = "4670",
                beschreibung = "Fahrtenbuch: ${normalized.stops.joinToString(" -> ") { it.label.ifBlank { it.address } }} | Zweck: $purpose | ${String.format(Locale.GERMANY, "%.1f", distance)} km | Quelle: ${source.name}",
                isEigenleistungSanierung = originalReceipt.isEigenleistungSanierung,
                wohneinheit = originalReceipt.wohneinheit,
                propertyId = propertyId
            )
        )
        val tripId = database.logbookDao().upsertTrip(
            com.example.data.LogbookTrip(
                date = tripDate,
                time = originalReceipt.uhrzeit,
                purpose = purpose,
                propertyReference = originalReceipt.wohneinheit,
                startAddress = startAddress,
                destinationAddress = destinationAddress,
                stopsJson = com.example.data.TripStopJson.encode(normalized.stops),
                routeMode = routeMode.name,
                sameReturnRoute = sameReturnRoute,
                taxDistanceKm = distance,
                kilometerSource = source.name,
                aiEstimatedKm = evidence.aiEstimatedKm,
                routedKm = evidence.routedKm,
                manualKm = evidence.manualKm,
                gpsMeasuredKm = evidence.gpsMeasuredKm,
                odometerStartKm = evidence.odometerStartKm,
                odometerEndKm = evidence.odometerEndKm,
                standardRouteId = standardRouteId,
                plausibilityStatus = decision.plausibilityStatus.name,
                manuallyConfirmed = evidence.manuallyConfirmed,
                sourceReceiptId = originalReceipt.id.takeIf { it > 0 },
                expenseReceiptId = expenseId.toInt(),
                routeProvider = routeResult?.providerId.orEmpty(),
                routeCalculatedAt = routeResult?.calculatedAt.orEmpty(),
                routeDurationSeconds = routeResult?.durationSeconds,
                correctionReason = correctionReason,
                correctionNote = correctionNote,
                routeSignature = normalized.signature,
                note = note,
                createdAt = now,
                updatedAt = now,
                propertyId = propertyId, bookingKey = effectiveKey
            )
        )
        tripId
    } }

    suspend fun correct(id: Long, date: String, purpose: String, km: Double, reason: String): Result<Unit> = runCatching {
        database.withTransaction {
            java.time.LocalDate.parse(date)
            require(purpose.isNotBlank() && reason.isNotBlank() && km.isFinite() && km > 0) { "Datum, Zweck, positive Kilometer und Änderungsgrund erforderlich." }
            val trip = requireNotNull(database.logbookDao().getTrip(id))
            require(trip.cancelledAt.isBlank()) { "Die Fahrt ist storniert." }
            val expense = editableExpense(trip)
            val now = java.time.Instant.now().toString()
            val history = history(trip, "Korrektur", reason, now)
            val updatedExpense = expense.copy(datum = date, bruttobetrag = km * 0.30,
                beschreibung = "Fahrtenbuch: ${trip.startAddress} → ${trip.destinationAddress} | Zweck: $purpose | $km km | Korrektur: $reason",
                freigabestatus = "OFFEN", allocationsJson = "", bookingProposalsJson = "", pruefstatus = "ZU_PRUEFEN",
                exportStatus = "ZU_PRUEFEN", exportlaufId = "", syncStatus = "PENDING", isArchivedToDrive = false)
            repository.insert(updatedExpense)
            database.logbookDao().upsertTrip(trip.copy(date = date, purpose = purpose, taxDistanceKm = km,
                kilometerSource = KilometerSource.MANUELL.name, manualKm = km, manuallyConfirmed = true,
                correctionReason = "Sonstiges", correctionNote = reason, historyJson = history, updatedAt = now))
        }
    }

    suspend fun cancel(id: Long, reason: String): Result<Unit> = runCatching {
        database.withTransaction {
            require(reason.isNotBlank()) { "Stornogrund erforderlich." }
            val trip = requireNotNull(database.logbookDao().getTrip(id))
            require(trip.cancelledAt.isBlank()) { "Die Fahrt ist bereits storniert." }
            val expense = editableExpense(trip)
            val now = java.time.Instant.now().toString()
            repository.softDelete(expense.id, "DELETE_PENDING", now, "LocalUser", java.util.UUID.randomUUID().toString(), "Fahrt storniert: $reason")
            database.logbookDao().upsertTrip(trip.copy(cancelledAt = now, historyJson = history(trip, "Storno", reason, now), updatedAt = now))
        }
    }

    private suspend fun editableExpense(trip: LogbookTrip): Receipt {
        val expense = requireNotNull(trip.expenseReceiptId?.let { repository.getReceiptById(it) }) { "Fahrtkostenbeleg fehlt." }
        require(expense.deletionStatus == "ACTIVE") { "Fahrtkostenbeleg ist bereits gelöscht." }
        require(expense.exportStatus != "EXPORTIERT" && expense.exportlaufId.isBlank()) { "Bereits exportierte Fahrtkosten bitte über eine separate Korrekturbuchung mit dem Steuerberater korrigieren." }
        require(BankReceiptDeletionPolicy.decide(expense.id, database.bankDao().getAllLinks()).allowed) { "Die Bankverknüpfung muss zuerst gelöst werden." }
        return expense
    }

    private fun history(trip: LogbookTrip, action: String, reason: String, time: String): String =
        JSONArray(trip.historyJson).put(JSONObject().put("action", action).put("reason", reason).put("at", time)
            .put("date", trip.date).put("purpose", trip.purpose).put("km", trip.taxDistanceKm)
            .put("source", trip.kilometerSource).put("previous", JSONObject(com.squareup.moshi.Moshi.Builder()
                .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(LogbookTrip::class.java).toJson(trip.copy(historyJson = "[]"))))).toString()
}
