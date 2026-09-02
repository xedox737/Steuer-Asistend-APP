package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.abs
import java.security.MessageDigest

enum class KilometerSource {
    KI_GESCHAETZT,
    ROUTE_BERECHNET,
    GPS_GEMESSEN,
    TACHO,
    STANDARDSTRECKE,
    MANUELL
}

enum class TripPlausibilityStatus {
    PLAUSIBEL,
    PRUEFEN,
    MANUELL_BESTAETIGT
}

enum class TripRouteMode {
    EINFACH,
    HIN_UND_RUECKFAHRT,
    INDIVIDUELL
}

@JsonClass(generateAdapter = true)
data class TripStop(
    val address: String,
    val label: String = "",
    val order: Int = 0
)

object TripStopJson {
    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter<List<TripStop>>(Types.newParameterizedType(List::class.java, TripStop::class.java))

    fun encode(stops: List<TripStop>): String = adapter.toJson(stops)

    fun decode(raw: String): List<TripStop> = if (raw.isBlank()) {
        emptyList()
    } else {
        runCatching { adapter.fromJson(raw).orEmpty() }.getOrDefault(emptyList())
    }
}

@Entity(tableName = "logbook_trips")
data class LogbookTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val time: String = "",
    val purpose: String,
    val propertyReference: String = "",
    val startAddress: String,
    val destinationAddress: String,
    val stopsJson: String = "",
    val routeMode: String = TripRouteMode.EINFACH.name,
    val sameReturnRoute: Boolean = false,
    val taxDistanceKm: Double,
    val kilometerSource: String,
    val aiEstimatedKm: Double? = null,
    val routedKm: Double? = null,
    val manualKm: Double? = null,
    val gpsMeasuredKm: Double? = null,
    val odometerStartKm: Double? = null,
    val odometerEndKm: Double? = null,
    val standardRouteId: Long? = null,
    val plausibilityStatus: String = TripPlausibilityStatus.PRUEFEN.name,
    val manuallyConfirmed: Boolean = false,
    val sourceReceiptId: Int? = null,
    val expenseReceiptId: Int? = null,
    val routeProvider: String = "",
    val routeCalculatedAt: String = "",
    val routeDurationSeconds: Long? = null,
    val correctionReason: String = "",
    val correctionNote: String = "",
    val routeSignature: String = "",
    val note: String = "",
    val createdAt: String,
    val updatedAt: String
) {
    val stops: List<TripStop> get() = TripStopJson.decode(stopsJson)
    val odometerDistanceKm: Double?
        get() = if (odometerStartKm != null && odometerEndKm != null && odometerEndKm >= odometerStartKm) {
            odometerEndKm - odometerStartKm
        } else {
            null
        }
}

@Entity(tableName = "standard_routes", indices = [Index(value = ["routeSignature"])])
data class StandardRoute(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startAddress: String,
    val destinationAddress: String,
    val stopsJson: String = "",
    val routeMode: String = TripRouteMode.EINFACH.name,
    val sameReturnRoute: Boolean = false,
    val distanceKm: Double,
    val active: Boolean = true,
    val routeSignature: String = "",
    val sourceProvider: String = "",
    val createdAt: String,
    val updatedAt: String
) {
    val stops: List<TripStop> get() = TripStopJson.decode(stopsJson)
}

@Dao
interface LogbookDao {
    @Query("SELECT * FROM logbook_trips ORDER BY date DESC, time DESC, id DESC")
    fun observeTrips(): Flow<List<LogbookTrip>>

    @Query("SELECT * FROM logbook_trips ORDER BY date ASC, time ASC, id ASC")
    suspend fun getAllTrips(): List<LogbookTrip>

    @Query("SELECT * FROM standard_routes ORDER BY id ASC")
    suspend fun getAllStandardRoutes(): List<StandardRoute>

    @Query("SELECT * FROM logbook_trips WHERE sourceReceiptId = :receiptId LIMIT 1")
    suspend fun getBySourceReceiptId(receiptId: Int): LogbookTrip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: LogbookTrip): Long

    @Query("DELETE FROM logbook_trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT * FROM standard_routes WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeStandardRoutes(): Flow<List<StandardRoute>>

    @Query("SELECT * FROM standard_routes WHERE active = 1 AND routeSignature = :signature ORDER BY updatedAt DESC LIMIT 1")
    suspend fun findStandardRoute(signature: String): StandardRoute?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStandardRoute(route: StandardRoute): Long

    @Query("DELETE FROM standard_routes WHERE id = :id")
    suspend fun deleteStandardRoute(id: Long)
}

data class RouteDistanceRequest(
    val stops: List<TripStop>,
    val routeMode: TripRouteMode,
    val sameReturnRoute: Boolean
)

data class RouteDistanceResult(
    val distanceKm: Double,
    val providerId: String,
    val calculatedAt: String,
    val durationSeconds: Long? = null
)

data class RouteDistanceAttempt(
    val result: RouteDistanceResult? = null,
    val errorMessage: String? = null
)

class RouteDistanceException(val userMessage: String) : Exception(userMessage)

fun interface RouteDistanceService {
    suspend fun calculateRoadDistance(request: RouteDistanceRequest): RouteDistanceResult?
}

/**
 * Safe default until a real routing provider is configured. It deliberately returns null:
 * no postal-code, hash or straight-line value is presented as a road distance.
 */
object UnavailableRouteDistanceService : RouteDistanceService {
    override suspend fun calculateRoadDistance(request: RouteDistanceRequest): RouteDistanceResult? = null
}

object RouteDistanceCoordinator {
    suspend fun resolve(
        request: RouteDistanceRequest,
        standardRoute: StandardRoute?,
        forceRecalculation: Boolean,
        service: RouteDistanceService
    ): RouteDistanceResult? {
        val signature = TripRouteNormalizer.normalize(request).signature
        if (!forceRecalculation && standardRoute?.active == true && standardRoute.routeSignature == signature) {
            return RouteDistanceResult(
                distanceKm = standardRoute.distanceKm,
                providerId = KilometerSource.STANDARDSTRECKE.name,
                calculatedAt = standardRoute.updatedAt
            )
        }
        return service.calculateRoadDistance(request)
    }
}

data class NormalizedTripRoute(
    val stops: List<TripStop>,
    val routeMode: TripRouteMode,
    val sameReturnRoute: Boolean,
    val signature: String
)

/** Single route definition shared by UI, routing, standard-route matching and export. */
object TripRouteNormalizer {
    fun normalize(
        startAddress: String,
        intermediateStops: List<TripStop>,
        destinationAddress: String,
        routeMode: TripRouteMode,
        sameReturnRoute: Boolean
    ): NormalizedTripRoute {
        val start = cleanDisplayAddress(startAddress)
        val destination = cleanDisplayAddress(destinationAddress)
        require(start.isNotBlank() && destination.isNotBlank()) { "Start- und Zieladresse müssen vollständig sein." }
        val result = mutableListOf(TripStop(start, "Start", 0))
        intermediateStops
            .sortedBy { it.order }
            .map { it.copy(address = cleanDisplayAddress(it.address)) }
            .filter { it.address.isNotBlank() }
            .forEach { result += it.copy(order = result.size, label = it.label.ifBlank { "Zwischenstopp" }) }
        result += TripStop(destination, "Ziel", result.size)

        // Variante B: Google receives the complete round trip. Never multiply afterwards.
        if (routeMode == TripRouteMode.HIN_UND_RUECKFAHRT && sameReturnRoute &&
            canonicalAddress(result.last().address) != canonicalAddress(start)
        ) {
            result += TripStop(start, "Rückkehr", result.size)
        }
        val canonical = buildString {
            append(routeMode.name.lowercase()).append('|').append(sameReturnRoute)
            result.forEach { append('|').append(canonicalAddress(it.address)) }
        }
        return NormalizedTripRoute(result, routeMode, sameReturnRoute, sha256(canonical))
    }

    fun normalize(request: RouteDistanceRequest): NormalizedTripRoute {
        require(request.stops.size >= 2) { "Für die Route werden mindestens Start und Ziel benötigt." }
        val ordered = request.stops.sortedBy { it.order }
        return normalize(
            ordered.first().address,
            ordered.drop(1).dropLast(1),
            ordered.last().address,
            request.routeMode,
            request.sameReturnRoute
        )
    }

    internal fun canonicalAddress(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun cleanDisplayAddress(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

data class DistanceEvidence(
    val aiEstimatedKm: Double? = null,
    val routedKm: Double? = null,
    val gpsMeasuredKm: Double? = null,
    val odometerStartKm: Double? = null,
    val odometerEndKm: Double? = null,
    val standardRouteKm: Double? = null,
    val manualKm: Double? = null,
    val manuallyConfirmed: Boolean = false,
    val correctionReason: String = ""
)

data class DistanceDecision(
    val taxDistanceKm: Double?,
    val source: KilometerSource?,
    val plausibilityStatus: TripPlausibilityStatus,
    val warnings: List<String>,
    val correctionReasonRequired: Boolean = false
)

object LogbookDistancePolicy {
    private const val MAX_REASONABLE_KM = 2_000.0
    private const val ABSOLUTE_TOLERANCE_KM = 2.0
    private const val RELATIVE_TOLERANCE = 0.15

    fun decide(evidence: DistanceEvidence): DistanceDecision {
        val warnings = mutableListOf<String>()
        val odometerKm = odometerDistance(evidence.odometerStartKm, evidence.odometerEndKm, warnings)
        val confirmedManual = evidence.manualKm.valid().takeIf { evidence.manuallyConfirmed }
        val candidates = listOfNotNull(
            confirmedManual?.let { KilometerSource.MANUELL to it },
            evidence.gpsMeasuredKm.valid()?.let { KilometerSource.GPS_GEMESSEN to it },
            odometerKm?.let { KilometerSource.TACHO to it },
            evidence.routedKm.valid()?.let { KilometerSource.ROUTE_BERECHNET to it },
            evidence.standardRouteKm.valid()?.let { KilometerSource.STANDARDSTRECKE to it },
            evidence.manualKm.valid()?.takeIf { confirmedManual == null }?.let { KilometerSource.MANUELL to it }
        )
        val selected = candidates.firstOrNull()

        if (selected == null && evidence.aiEstimatedKm.valid() != null) {
            warnings += "Die KI-Strecke ist nur ein Vorschlag und muss durch Route, GPS, Tacho, Standardstrecke oder manuelle Eingabe bestätigt werden."
        }
        selected?.second?.let { chosen ->
            compare("KI-Schätzung", evidence.aiEstimatedKm.valid(), chosen, warnings)
            compare("Google-/Straßenroute", evidence.routedKm.valid(), chosen, warnings)
            compare("GPS-Messung", evidence.gpsMeasuredKm.valid(), chosen, warnings)
            compare("Tachowert", odometerKm, chosen, warnings)
            compare("Standardstrecke", evidence.standardRouteKm.valid(), chosen, warnings)
            compare("Manuelle Strecke", evidence.manualKm.valid(), chosen, warnings)
            if (chosen > MAX_REASONABLE_KM) warnings += "Die Strecke ist ungewöhnlich lang und sollte geprüft werden."
        }

        val strongestAutomatic = listOfNotNull(
            evidence.gpsMeasuredKm.valid(), odometerKm, evidence.routedKm.valid(), evidence.standardRouteKm.valid()
        ).firstOrNull()
        val correctionReasonRequired = evidence.manualKm.valid()?.let { manual ->
            strongestAutomatic?.let { materiallyDifferent(manual, it) }
        } == true
        if (correctionReasonRequired && evidence.correctionReason.isBlank()) {
            warnings += "Für die deutlich abweichende manuelle Strecke ist ein Korrekturgrund erforderlich."
        }

        val status = when {
            evidence.manuallyConfirmed && selected != null && (!correctionReasonRequired || evidence.correctionReason.isNotBlank()) -> TripPlausibilityStatus.MANUELL_BESTAETIGT
            selected != null && warnings.isEmpty() -> TripPlausibilityStatus.PLAUSIBEL
            else -> TripPlausibilityStatus.PRUEFEN
        }
        return DistanceDecision(selected?.second, selected?.first, status, warnings.distinct(), correctionReasonRequired)
    }

    private fun compare(label: String, value: Double?, chosen: Double, warnings: MutableList<String>) {
        if (value != null && value != chosen && materiallyDifferent(value, chosen)) {
            warnings += "$label weicht deutlich von der verwendeten Strecke ab."
        }
    }

    private fun materiallyDifferent(first: Double, second: Double): Boolean =
        abs(first - second) > maxOf(ABSOLUTE_TOLERANCE_KM, second * RELATIVE_TOLERANCE)

    private fun Double?.valid(): Double? = this?.takeIf { it.isFinite() && it > 0.0 }

    private fun odometerDistance(start: Double?, end: Double?, warnings: MutableList<String>): Double? {
        if (start == null && end == null) return null
        if (start == null || end == null) {
            warnings += "Für die Tachoberechnung fehlen Start- oder Endstand."
            return null
        }
        if (!start.isFinite() || !end.isFinite() || start < 0.0 || end < 0.0) {
            warnings += "Tachostände müssen gültige, nicht negative Werte sein."
            return null
        }
        if (end < start) {
            warnings += "Der Endtachostand darf nicht kleiner als der Starttachostand sein."
            return null
        }
        return (end - start).takeIf { it > 0.0 }
    }
}

interface GpsTripRecorder {
    val isAvailable: Boolean
    suspend fun start(): Result<Unit>
    suspend fun stop(): Result<Double>
}

object DisabledGpsTripRecorder : GpsTripRecorder {
    override val isAvailable: Boolean = false
    override suspend fun start(): Result<Unit> =
        Result.failure(IllegalStateException("GPS-Aufzeichnung ist in diesem Entwicklungsblock nicht aktiviert."))

    override suspend fun stop(): Result<Double> =
        Result.failure(IllegalStateException("Keine GPS-Fahrt gestartet."))
}
