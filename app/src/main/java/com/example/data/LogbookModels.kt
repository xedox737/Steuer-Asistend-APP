package com.example.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.abs

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
    val gpsMeasuredKm: Double? = null,
    val odometerStartKm: Double? = null,
    val odometerEndKm: Double? = null,
    val standardRouteId: Long? = null,
    val plausibilityStatus: String = TripPlausibilityStatus.PRUEFEN.name,
    val manuallyConfirmed: Boolean = false,
    val sourceReceiptId: Int? = null,
    val expenseReceiptId: Int? = null,
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

@Entity(tableName = "standard_routes")
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
    val createdAt: String,
    val updatedAt: String
)

@Dao
interface LogbookDao {
    @Query("SELECT * FROM logbook_trips ORDER BY date DESC, time DESC, id DESC")
    fun observeTrips(): Flow<List<LogbookTrip>>

    @Query("SELECT * FROM logbook_trips ORDER BY date ASC, time ASC, id ASC")
    suspend fun getAllTrips(): List<LogbookTrip>

    @Query("SELECT * FROM logbook_trips WHERE sourceReceiptId = :receiptId LIMIT 1")
    suspend fun getBySourceReceiptId(receiptId: Int): LogbookTrip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrip(trip: LogbookTrip): Long

    @Query("DELETE FROM logbook_trips WHERE id = :id")
    suspend fun deleteTrip(id: Long)

    @Query("SELECT * FROM standard_routes WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeStandardRoutes(): Flow<List<StandardRoute>>

    @Query("""
        SELECT * FROM standard_routes
        WHERE active = 1
          AND lower(trim(startAddress)) = lower(trim(:start))
          AND lower(trim(destinationAddress)) = lower(trim(:destination))
        ORDER BY updatedAt DESC
        LIMIT 1
    """)
    suspend fun findStandardRoute(start: String, destination: String): StandardRoute?

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
    val calculatedAt: String
)

interface RouteDistanceService {
    suspend fun calculateRoadDistance(request: RouteDistanceRequest): RouteDistanceResult?
}

/**
 * Safe default until a real routing provider is configured. It deliberately returns null:
 * no postal-code, hash or straight-line value is presented as a road distance.
 */
object UnavailableRouteDistanceService : RouteDistanceService {
    override suspend fun calculateRoadDistance(request: RouteDistanceRequest): RouteDistanceResult? = null
}

data class DistanceEvidence(
    val aiEstimatedKm: Double? = null,
    val routedKm: Double? = null,
    val gpsMeasuredKm: Double? = null,
    val odometerStartKm: Double? = null,
    val odometerEndKm: Double? = null,
    val standardRouteKm: Double? = null,
    val manualKm: Double? = null,
    val manuallyConfirmed: Boolean = false
)

data class DistanceDecision(
    val taxDistanceKm: Double?,
    val source: KilometerSource?,
    val plausibilityStatus: TripPlausibilityStatus,
    val warnings: List<String>
)

object LogbookDistancePolicy {
    private const val MAX_REASONABLE_KM = 2_000.0
    private const val ABSOLUTE_TOLERANCE_KM = 2.0
    private const val RELATIVE_TOLERANCE = 0.15

    fun decide(evidence: DistanceEvidence): DistanceDecision {
        val warnings = mutableListOf<String>()
        val odometerKm = odometerDistance(evidence.odometerStartKm, evidence.odometerEndKm, warnings)
        val candidates = listOfNotNull(
            evidence.gpsMeasuredKm.valid()?.let { KilometerSource.GPS_GEMESSEN to it },
            odometerKm?.let { KilometerSource.TACHO to it },
            evidence.routedKm.valid()?.let { KilometerSource.ROUTE_BERECHNET to it },
            evidence.standardRouteKm.valid()?.let { KilometerSource.STANDARDSTRECKE to it },
            evidence.manualKm.valid()?.let { KilometerSource.MANUELL to it }
        )
        val selected = candidates.firstOrNull()

        if (selected == null && evidence.aiEstimatedKm.valid() != null) {
            warnings += "Die KI-Strecke ist nur ein Vorschlag und muss durch Route, GPS, Tacho, Standardstrecke oder manuelle Eingabe bestätigt werden."
        }
        selected?.second?.let { chosen ->
            listOfNotNull(
                evidence.aiEstimatedKm.valid(),
                evidence.routedKm.valid(),
                evidence.gpsMeasuredKm.valid(),
                odometerKm,
                evidence.standardRouteKm.valid(),
                evidence.manualKm.valid()
            ).filter { it != chosen }.forEach { other ->
                val tolerance = maxOf(ABSOLUTE_TOLERANCE_KM, chosen * RELATIVE_TOLERANCE)
                if (abs(other - chosen) > tolerance) {
                    warnings += "Kilometerangaben können deutlich voneinander abweichen."
                }
            }
            if (chosen > MAX_REASONABLE_KM) warnings += "Die Strecke ist ungewöhnlich lang und sollte geprüft werden."
        }

        val status = when {
            evidence.manuallyConfirmed && selected != null -> TripPlausibilityStatus.MANUELL_BESTAETIGT
            selected != null && warnings.isEmpty() -> TripPlausibilityStatus.PLAUSIBEL
            else -> TripPlausibilityStatus.PRUEFEN
        }
        return DistanceDecision(selected?.second, selected?.first, status, warnings.distinct())
    }

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
