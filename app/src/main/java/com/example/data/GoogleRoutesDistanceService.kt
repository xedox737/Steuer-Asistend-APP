package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class RoutesHttpResponse(val code: Int, val body: String)

internal fun interface RoutesHttpTransport {
    suspend fun post(url: String, headers: Map<String, String>, body: String): RoutesHttpResponse
}

internal class OkHttpRoutesTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) : RoutesHttpTransport {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): RoutesHttpResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .build()
            client.newCall(request).execute().use { response ->
                RoutesHttpResponse(response.code, response.body?.string().orEmpty())
            }
        }
}

/** Real DRIVE road routing via Google Routes v2; no straight-line fallback is used. */
class GoogleRoutesDistanceService internal constructor(
    private val packageName: String,
    private val signingCertificateSha1: String,
    private val apiKeyProvider: () -> CharArray?,
    private val transport: RoutesHttpTransport,
    private val now: () -> Instant = Instant::now
) : RouteDistanceService {
    constructor(context: Context, apiKeyProvider: () -> CharArray?) : this(
        packageName = context.packageName,
        signingCertificateSha1 = androidCertificateSha1(context),
        apiKeyProvider = apiKeyProvider,
        transport = OkHttpRoutesTransport()
    )

    override suspend fun calculateRoadDistance(request: RouteDistanceRequest): RouteDistanceResult? {
        val route = try {
            TripRouteNormalizer.normalize(request)
        } catch (e: IllegalArgumentException) {
            throw RouteDistanceException(e.message ?: "Die Route enthält ungültige Adressen.")
        }
        val keyChars = apiKeyProvider()
            ?: throw RouteDistanceException("Für Google Routes ist noch kein API-Schlüssel eingerichtet.")
        val key = keyChars.concatToString().trim()
        try {
            if (key.length < 16 || key.any(Char::isWhitespace)) {
                throw RouteDistanceException("Der Google-Routes-API-Schlüssel ist ungültig.")
            }
            val body = requestBody(route.stops).toString()
            val headers = linkedMapOf(
                "X-Goog-Api-Key" to key,
                "X-Goog-FieldMask" to FIELD_MASK
            ).apply {
                if (packageName.isNotBlank()) put("X-Android-Package", packageName)
                if (signingCertificateSha1.isNotBlank()) put("X-Android-Cert", signingCertificateSha1)
            }
            val response = try {
                transport.post(ENDPOINT, headers, body)
            } catch (_: SocketTimeoutException) {
                throw RouteDistanceException("Zeitüberschreitung bei der Google-Routenberechnung.")
            } catch (_: IOException) {
                throw RouteDistanceException("Google-Routenberechnung derzeit nicht verfügbar. Bitte Internetverbindung prüfen.")
            }
            if (response.code !in 200..299) throw mapHttpError(response.code)

            val root = runCatching { JSONObject(response.body) }.getOrElse {
                throw RouteDistanceException("Google Routes hat keine gültige Antwort geliefert.")
            }
            validateGeocoding(root.optJSONObject("geocodingResults"))
            val routeJson = root.optJSONArray("routes")?.optJSONObject(0)
                ?: throw RouteDistanceException("Die Route konnte für diese Adressen nicht gefunden werden.")
            val meters = routeJson.optLong("distanceMeters", 0L)
            if (meters <= 0L) throw RouteDistanceException("Google Routes hat keine fahrbare Strecke geliefert.")
            return RouteDistanceResult(
                distanceKm = meters / 1000.0,
                providerId = PROVIDER_ID,
                calculatedAt = now().toString(),
                durationSeconds = parseDurationSeconds(routeJson.optString("duration"))
            )
        } finally {
            keyChars.fill('\u0000')
        }
    }

    private fun requestBody(stops: List<TripStop>): JSONObject = JSONObject().apply {
        put("origin", waypoint(stops.first().address))
        put("destination", waypoint(stops.last().address))
        if (stops.size > 2) {
            put("intermediates", JSONArray().apply {
                stops.subList(1, stops.lastIndex).forEach { put(waypoint(it.address)) }
            })
        }
        put("travelMode", "DRIVE")
        put("computeAlternativeRoutes", false)
        put("languageCode", "de-DE")
        put("units", "METRIC")
    }

    private fun waypoint(address: String) = JSONObject().put("address", address)

    private fun validateGeocoding(results: JSONObject?) {
        if (results == null) return
        val entries = buildList {
            results.optJSONObject("origin")?.let(::add)
            results.optJSONArray("intermediates")?.let { array ->
                for (index in 0 until array.length()) array.optJSONObject(index)?.let(::add)
            }
            results.optJSONObject("destination")?.let(::add)
        }
        if (entries.any { it.optBoolean("partialMatch", false) }) {
            throw RouteDistanceException("Mindestens eine Adresse ist nicht eindeutig. Bitte Adresse prüfen.")
        }
        if (entries.any { it.has("geocoderStatus") && it.optJSONObject("geocoderStatus")?.optInt("code", 0) != 0 }) {
            throw RouteDistanceException("Mindestens eine Adresse konnte nicht aufgelöst werden.")
        }
    }

    private fun mapHttpError(code: Int): RouteDistanceException = when (code) {
        400 -> RouteDistanceException("Die Route enthält eine ungültige oder nicht gefundene Adresse.")
        401, 403 -> RouteDistanceException("Google Routes API ist nicht korrekt konfiguriert. Schlüssel, API-Aktivierung und Billing prüfen.")
        429 -> RouteDistanceException("Das Google-Routes-Kontingent ist ausgeschöpft. Bitte später erneut versuchen.")
        in 500..599 -> RouteDistanceException("Google-Routenberechnung derzeit nicht verfügbar.")
        else -> RouteDistanceException("Google-Routenberechnung fehlgeschlagen (HTTP $code).")
    }

    private fun parseDurationSeconds(value: String): Long? =
        value.removeSuffix("s").toDoubleOrNull()?.toLong()?.takeIf { it >= 0L }

    companion object {
        const val PROVIDER_ID = "GOOGLE_ROUTES"
        internal const val ENDPOINT = "https://routes.googleapis.com/directions/v2:computeRoutes"
        private const val FIELD_MASK = "routes.distanceMeters,routes.duration,geocodingResults"

        private fun androidCertificateSha1(context: Context): String = runCatching {
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners.orEmpty()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures.orEmpty()
            }
            signatures.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-1").digest(bytes)
                    .joinToString(":") { "%02X".format(it) }
            }.orEmpty()
        }.getOrDefault("")
    }
}
