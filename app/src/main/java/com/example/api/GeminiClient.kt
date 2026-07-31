package com.example.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.HttpException
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay

// --- Gemini Custom Exceptions ---
sealed class GeminiAnalysisException(
    val category: String,
    val errorCode: String,
    val userMessage: String,
    val httpStatus: Int? = null,
    val cleanedMessage: String? = null
) : Exception(userMessage) {
    class KeyMissing : GeminiAnalysisException(
        category = "CONFIG",
        errorCode = "KEY_MISSING",
        userMessage = "Für die KI-Analyse ist noch kein Gemini-Zugang eingerichtet."
    )
    class KeyInvalid(cleanedMsg: String? = null) : GeminiAnalysisException(
        category = "AUTH",
        errorCode = "KEY_INVALID",
        userMessage = "Die Gemini-Anmeldung oder API-Berechtigung ist ungültig.",
        httpStatus = 401,
        cleanedMessage = cleanedMsg
    )
    class PermissionDenied(cleanedMsg: String? = null) : GeminiAnalysisException(
        category = "AUTH",
        errorCode = "PERMISSION_DENIED",
        userMessage = "Die Gemini-Anmeldung oder API-Berechtigung ist ungültig.",
        httpStatus = 403,
        cleanedMessage = cleanedMsg
    )
    class QuotaExceeded(cleanedMsg: String? = null) : GeminiAnalysisException(
        category = "QUOTA",
        errorCode = "RESOURCE_EXHAUSTED",
        userMessage = "Das Gemini-Kontingent ist momentan ausgeschöpft. Bitte später erneut versuchen.",
        httpStatus = 429,
        cleanedMessage = cleanedMsg
    )
    class RequestTooLarge(cleanedMsg: String? = null) : GeminiAnalysisException(
        category = "LIMIT",
        errorCode = "REQUEST_TOO_LARGE",
        userMessage = "Anfrage zu groß. Bitte ein kleineres Dokument oder weniger Seiten wählen.",
        httpStatus = 400,
        cleanedMessage = cleanedMsg
    )
    class NetworkError(cause: Throwable) : GeminiAnalysisException(
        category = "NETWORK",
        errorCode = "NETWORK_ERROR",
        userMessage = "Netzwerkfehler: Bitte überprüfe deine Internetverbindung.",
        cleanedMessage = cause.localizedMessage
    )
    class TimeoutError(cause: Throwable) : GeminiAnalysisException(
        category = "NETWORK",
        errorCode = "TIMEOUT",
        userMessage = "Zeitüberschreitung bei der Anfrage. Bitte versuche es noch einmal.",
        cleanedMessage = cause.localizedMessage
    )
    class InvalidResponse(msg: String) : GeminiAnalysisException(
        category = "PARSING",
        errorCode = "INVALID_RESPONSE",
        userMessage = "Ungültige KI-Antwort erhalten. Bitte erneut analysieren.",
        cleanedMessage = msg
    )
    class GenericError(errorCode: String, userMessage: String, httpStatus: Int? = null, cleanedMsg: String? = null) : GeminiAnalysisException(
        category = "GENERIC",
        errorCode = errorCode,
        userMessage = userMessage,
        httpStatus = httpStatus,
        cleanedMessage = cleanedMsg
    )
}

// --- Gemini API Request & Response Models ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<Content>,
    val generationConfig: GenerationConfig? = null,
    val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class Part(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@JsonClass(generateAdapter = true)
data class InlineData(
    val mimeType: String,
    val data: String
)

@JsonClass(generateAdapter = true)
data class GenerationConfig(
    val responseMimeType: String? = null,
    val temperature: Double? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<Candidate>?
)

@JsonClass(generateAdapter = true)
data class Candidate(
    val content: Content?
)

// --- Extracted Receipt Target Model ---

@JsonClass(generateAdapter = true)
data class ExtractedReceipt(
    @Json(name = "aussteller") val aussteller: String = "",
    @Json(name = "datum") val datum: String = "", // YYYY-MM-DD
    @Json(name = "uhrzeit") val uhrzeit: String = "", // HH:MM or empty
    @Json(name = "bruttobetrag") val bruttobetrag: Double = 0.0,
    @Json(name = "hauptkategorie") val hauptkategorie: String = "",
    @Json(name = "unterkategorie") val unterkategorie: String = "",
    @Json(name = "kontoNr") val kontoNr: String = "",
    @Json(name = "beschreibung") val beschreibung: String = "",
    @Json(name = "isEigenleistungSanierung") val isEigenleistungSanierung: Boolean = false,
    @Json(name = "wohneinheit") val wohneinheit: String = "",
    @Json(name = "mieter") val mieter: String = "",
    @Json(name = "positionen") val positionen: List<com.example.data.ReceiptItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class AiSearchResult(
    @Json(name = "answer") val answer: String = "",
    @Json(name = "totalAmount") val totalAmount: Double? = null,
    @Json(name = "matchingReceiptIds") val matchingReceiptIds: List<Long> = emptyList()
)

// --- 5 New AI Feature Response Models ---

@JsonClass(generateAdapter = true)
data class TaxFinding(
    @Json(name = "category") val category: String = "",
    @Json(name = "title") val title: String = "",
    @Json(name = "description") val description: String = "",
    @Json(name = "severity") val severity: String = "INFO", // INFO, WARNUNG, GEFAHR
    @Json(name = "actionRecommendation") val actionRecommendation: String = ""
)

@JsonClass(generateAdapter = true)
data class TaxPlausibilityReport(
    @Json(name = "overallStatus") val overallStatus: String = "OK",
    @Json(name = "sanierungCostSum") val sanierungCostSum: Double = 0.0,
    @Json(name = "sanierungCostLimit") val sanierungCostLimit: Double = 0.0,
    @Json(name = "sanierungLimitPercentage") val sanierungLimitPercentage: Double = 0.0,
    @Json(name = "is15PercentLimitExceeded") val is15PercentLimitExceeded: Boolean = false,
    @Json(name = "findings") val findings: List<TaxFinding> = emptyList(),
    @Json(name = "summaryText") val summaryText: String = ""
)

@JsonClass(generateAdapter = true)
data class UtilityCostItem(
    @Json(name = "category") val category: String = "",
    @Json(name = "totalBuildingCost") val totalBuildingCost: Double = 0.0,
    @Json(name = "costKey") val costKey: String = "",
    @Json(name = "tenantShare") val tenantShare: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class TenantUtilityStatement(
    @Json(name = "tenantName") val tenantName: String = "",
    @Json(name = "unitName") val unitName: String = "",
    @Json(name = "period") val period: String = "",
    @Json(name = "totalOperatingCosts") val totalOperatingCosts: Double = 0.0,
    @Json(name = "tenantShareAmount") val tenantShareAmount: Double = 0.0,
    @Json(name = "prepaymentsAmount") val prepaymentsAmount: Double = 0.0,
    @Json(name = "balanceAmount") val balanceAmount: Double = 0.0,
    @Json(name = "costBreakdown") val costBreakdown: List<UtilityCostItem> = emptyList(),
    @Json(name = "formalDraftText") val formalDraftText: String = ""
)

@JsonClass(generateAdapter = true)
data class UnitRentOptimization(
    @Json(name = "unitName") val unitName: String = "",
    @Json(name = "currentRent") val currentRent: Double = 0.0,
    @Json(name = "suggestedRent") val suggestedRent: Double = 0.0,
    @Json(name = "reasoning") val reasoning: String = "",
    @Json(name = "legalBasis") val legalBasis: String = ""
)

@JsonClass(generateAdapter = true)
data class RentYieldOptimizationReport(
    @Json(name = "currentTotalRentNet") val currentTotalRentNet: Double = 0.0,
    @Json(name = "recommendedTotalRentNet") val recommendedTotalRentNet: Double = 0.0,
    @Json(name = "currentGrossYieldPct") val currentGrossYieldPct: Double = 0.0,
    @Json(name = "potentialGrossYieldPct") val potentialGrossYieldPct: Double = 0.0,
    @Json(name = "unitOptimizations") val unitOptimizations: List<UnitRentOptimization> = emptyList(),
    @Json(name = "summaryText") val summaryText: String = ""
)

@JsonClass(generateAdapter = true)
data class DamageAssessmentResult(
    @Json(name = "damageTitle") val damageTitle: String = "",
    @Json(name = "severity") val severity: String = "Mittel",
    @Json(name = "taxCategory") val taxCategory: String = "Erhaltungsaufwand",
    @Json(name = "estimatedRepairCostRange") val estimatedRepairCostRange: String = "",
    @Json(name = "urgency") val urgency: String = "Normal",
    @Json(name = "recommendedAction") val recommendedAction: String = "",
    @Json(name = "craftsmanDraftLetter") val craftsmanDraftLetter: String = ""
)

@JsonClass(generateAdapter = true)
data class ContractAnalysisResult(
    @Json(name = "contractType") val contractType: String = "Mietvertrag",
    @Json(name = "parties") val parties: String = "",
    @Json(name = "startDate") val startDate: String = "",
    @Json(name = "noticePeriod") val noticePeriod: String = "",
    @Json(name = "rentAdjustmentClause") val rentAdjustmentClause: String = "",
    @Json(name = "depositTerms") val depositTerms: String = "",
    @Json(name = "importantClauses") val importantClauses: List<String> = emptyList(),
    @Json(name = "riskFlags") val riskFlags: List<String> = emptyList(),
    @Json(name = "summary") val summary: String = ""
)

@JsonClass(generateAdapter = true)
data class BankStatementMatchItem(
    @Json(name = "date") val date: String = "",
    @Json(name = "counterparty") val counterparty: String = "",
    @Json(name = "amount") val amount: Double = 0.0,
    @Json(name = "isIncome") val isIncome: Boolean = false,
    @Json(name = "purpose") val purpose: String = "",
    @Json(name = "matchedReceiptId") val matchedReceiptId: Long? = null,
    @Json(name = "matchedTenantName") val matchedTenantName: String? = null,
    @Json(name = "status") val status: String = "MATCHED", // MATCHED, MISSING_RECEIPT, RENT_ARREARS, UNMATCHED
    @Json(name = "notes") val notes: String = ""
)

@JsonClass(generateAdapter = true)
data class BankStatementReconciliationResult(
    @Json(name = "period") val period: String = "",
    @Json(name = "totalIncoming") val totalIncoming: Double = 0.0,
    @Json(name = "totalOutgoing") val totalOutgoing: Double = 0.0,
    @Json(name = "matchedCount") val matchedCount: Int = 0,
    @Json(name = "missingReceiptsCount") val missingReceiptsCount: Int = 0,
    @Json(name = "rentArrearsCount") val rentArrearsCount: Int = 0,
    @Json(name = "items") val items: List<BankStatementMatchItem> = emptyList(),
    @Json(name = "summary") val summary: String = ""
)

// --- Retrofit API Service ---

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val TAG = "GeminiClient"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"
    private const val MAX_RETRY_ATTEMPTS = 2
    private const val INITIAL_RETRY_DELAY_MS = 500L
    private const val MAX_RECEIPT_PAGES = 20
    private const val MAX_IMAGE_BYTES = 20 * 1024 * 1024
    private val modelName: String =
        BuildConfig.GEMINI_MODEL.trim().ifEmpty { "gemini-3.5-flash" }

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    private suspend fun generateContentWithRetry(
        apiKey: String,
        request: GeminiRequest
    ): GeminiResponse {
        var attempt = 0
        while (true) {
            try {
                return service.generateContent(modelName, apiKey, request)
            } catch (e: HttpException) {
                val retryable = e.code() == 429 || e.code() == 503
                if (!retryable || attempt >= MAX_RETRY_ATTEMPTS) throw e
                delay(INITIAL_RETRY_DELAY_MS * (1L shl attempt))
                attempt++
            }
        }
    }

    // Helper to convert Bitmap to Base64
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun sanitizeErrorBody(body: String?): String {
        if (body == null) return ""
        return body.replace(Regex("AIzaSy[A-Za-z0-9_\\-]{33}"), "[REDACTED_KEY]")
    }

    /**
     * Sends the receipt details or image to Gemini for structured extraction.
     */
    suspend fun analyzeReceipt(
        receiptText: String? = null,
        bitmap: Bitmap? = null,
        bitmaps: List<Bitmap>? = null,
        userLearnedRulesContext: String? = null
    ): ExtractedReceipt? {
        val startTime = System.currentTimeMillis()
        val suppliedBitmaps = buildList {
            bitmap?.let(::add)
            bitmaps?.let(::addAll)
        }
        if (suppliedBitmaps.size > MAX_RECEIPT_PAGES ||
            suppliedBitmaps.sumOf { it.byteCount } > MAX_IMAGE_BYTES
        ) {
            throw GeminiAnalysisException.RequestTooLarge(
                "Maximal $MAX_RECEIPT_PAGES Seiten beziehungsweise 20 MB Bilddaten sind erlaubt."
            )
        }
        val mimeType = if (bitmap != null || bitmaps?.isNotEmpty() == true) "image/jpeg" else "text/plain"
        val dataSize = if (bitmap != null) {
            "Approx " + (bitmap.byteCount / 1024) + " KB"
        } else if (bitmaps?.isNotEmpty() == true) {
            "Approx " + (bitmaps.sumOf { it.byteCount } / 1024) + " KB"
        } else {
            "${receiptText?.length ?: 0} chars"
        }

        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "--- GEMINI API CALL DIAGNOSTICS ---")
            Log.e(TAG, "Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent")
            Log.e(TAG, "Model: ${modelName}")
            Log.e(TAG, "File Type: $mimeType")
            Log.e(TAG, "File Size: $dataSize")
            Log.e(TAG, "Duration: ${duration}ms")
            Log.e(TAG, "Error Category: CONFIG")
            Log.e(TAG, "Error Code: KEY_MISSING")
            Log.e(TAG, "Cleaned Error Message: Key is empty or placeholder")
            throw GeminiAnalysisException.KeyMissing()
        }

        val parts = mutableListOf<Part>()
        if (receiptText != null) {
            parts.add(Part(text = "Hier sind Belegtext-Daten:\n$receiptText"))
        }
        if (bitmap != null) {
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bitmap.toBase64())))
        }
        bitmaps?.forEach { bmp ->
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = bmp.toBase64())))
        }

        parts.add(Part(text = "Bitte analysiere diesen Beleg / diese Rechnung und extrahiere die Kerndaten präzise nach den Systemvorgaben. Falls mehrere Seiten vorliegen, führe alle Informationen zusammen."))

        val learnedRulesSection = if (!userLearnedRulesContext.isNullOrBlank()) {
            """
            
            GELERNTES BENUTZER-WISSEN (AUS BISHERIGEN BELEGEN UND INDIVIDUELLEN REGELN):
            Der Benutzer hat für bestimmte Händler, Märkte oder Kategorien individuelle Zuordnungen gelernt. WENN der Beleg zu einem dieser Händler passt, ORDNE IHN BEVORZUGT GEMÄSS DIESEN GELERNTE REGELN EIN:
            $userLearnedRulesContext
            """.trimIndent()
        } else ""

        val systemInstruction = """
            Du bist ein hochpräziser digitaler Steuer- und Buchhaltungsassistent für die Verwaltung eines deutschen 7-Familienhauses (Anlage V).
            Analysiere den Beleg, die Rechnung oder Quittung und extrahiere die Kerndaten fehlerfrei. Ordne die Ausgaben exakt in die vorgegebene Taxonomie ein.

            KONTEXT ZUM OBJEKT:
            - Notarieller Kaufvertrag: 01.10.2025.
            - Übergang von Nutzen und Lasten: 01.01.2026.
            - Sanierung: Eine Wohnung wurde ab 01.10.2025 bis 31.01.2026 in reiner Eigenleistung (nur Material, keine Handwerker) saniert.
            $learnedRulesSection

            STRIKTE REGELN FÜR DIE KATEGORISIERUNG:
            
            Hauptkategorien (hauptkategorie) MÜSSEN exakt einer dieser Werte sein:
            1. "Anschaffungskosten"
            2. "Betriebs- / Nebenkosten"
            3. "Finanzierung, Kredite & Versicherungen"
            4. "Miete, Nebenkosten & Kaution"
            5. "Renovierungs- / Reparaturkosten & Investitionen"
            6. "Sonstige Ausgaben"
            7. "Sonstige Einnahmen"

            Unterkategorien (unterkategorie) MÜSSEN exakt einer dieser Werte sein:
            "Abbruchkosten", "Abwasser", "Allgemeinstrom", "Antenne/Kabelanschluss", 
            "Anwaltskosten", "Architekt", "Aufzug sowie Aufzugswartung", "Ausstattung", 
            "Außenanlagen", "Bad", "Balkon & Terasse", "Baukosten", "Betriebskosten", 
            "Dach & Fassade", "Einkommensteuer", "Einmalige Ungezieferbekämpfung", 
            "Einnahmen aus Münzwaschgeräten", "Einrichtungen der Wäschepflege", 
            "Einzahlung Kaution", "Elektrik & Beleuchtung", "Entsorgung Hausrat", 
            "Entwässerung und Niederschlagswasser", "Erbpachtzins", "Erschließungskosten", 
            "Fahrtkosten", "Fassadenreinigung", "Fenster, Tür & Boden", "Frischwasser", 
            "Fußwegreinigung", "Garage & Stellplätze", "Gartenpflege", "Gebäudereinigung", 
            "Gebäudeversicherung", "Gebühren", "Geldbeschaffungskosten", "Gerichtskosten", 
            "Grundbuchgebühren", "Grunderwerbsteuer", "Grundsteuer", "Gutachter", 
            "Guthabenzins", "Gutschrift aus Betriebskostenabrechnung", "Hausgeld und WEG Nebenkosten", 
            "Hausverwaltungskosten", "Hauswart/Hausmeister", "Heiz- und Warmwasserkosten", 
            "Heizkosten", "Heizung & Therme", "Innenbereich", "Inserate", 
            "Instandhaltungsrücklage", "Kaltmiete", "Kapitalertragssteuer", "Kaufpreis Garagen", 
            "Kaufpreis Objekt", "Kaufpreis Sonstiges", "Kaufpreis Stellplatz", "Kaution", 
            "Kontoführungsgebühren", "Kosten für Brennstoffe", "Kostenaufwand für leerstehende Räumlichkeiten", 
            "Kreditauszahlung", "Kreditrate", "Kredittilgung", "Kreditzinsen", 
            "Legionellenuntersuchung", "Maklerprovision", "Mietzuschlag", "Müllbeseitigung", 
            "Nachzahlung aus Betriebskostenabrechnung", "Notarkosten", "Nutzerwechselgebühren", 
            "Pauschalmiete", "Privateinlage", "Privatentnahme", "Rechtsberatungskosten", 
            "Rechtsschutzversicherung", "Regelmäßige Dachrinnenreinigung & Fassadenreinigung", 
            "Regelmäßige Ungezieferbekämpfung", "Reinigung Öltank", "Reinigungskosten", 
            "Rückzahlung Kaution", "Sach- und Haftpflichtversicherung", "Sanitär", 
            "Schadensbeseitigung", "Schornsteinreinigung", "Sondertilgung", "Sonstige", 
            "Sonstige Betriebskosten", "Sonstige Einrichtungen", "Sonstige Versicherungen", 
            "Sonstiges", "Sperrmüllentsorgung", "Stellplatz, Garage, Keller", "Steuerberatungskosten", 
            "Straßenreinigung", "Streichen, Tapezieren", "Thermenwartung", "Umsatzsteuer bei Gewerbe", 
            "Umsatzsteuer-Vorauszahlung", "Vermesser", "Vermietung", "Verwaltungskosten des Vermieters", 
            "Verwaltungskosten für Sozialwohnungen", "Vorfälligkeitsentschädigung", "Wachdienst / Pförtner", 
            "Warmmiete", "Warmwasserkosten", "Wartung Rauchmelder & Feuerlöscher", 
            "Wartung der Heizungsanlage / Thermen", "Winterdienst", "Wärme- & Schalldämmung"

            STRIKTE REGELN FÜR KONTO-NUMMERN (kontoNr):
            - Rechnungen für Notar (Kaufvertrag!), Grunderwerbsteuer, Grundbuchamt und Makler -> "0050".
            - Rechnungen für Kreditzinsen -> "2110".
            - Rechnungen für Geldbeschaffungskosten (Notar Grundschuld!) -> "2120".
            - Rechnungen für Kontoführungsgebühren -> "4970".
            - Belege für Baumarkt-Materialien, Sanierung (Sanitär, Elektrik, Boden, Streichen etc.) -> "4830".
            - Tankbelege oder sonstige Fahrtkostenbelege -> "4670".
            - Andere Nebenkosten oder Gebühren -> "4970" (oder passend).

            EXTRAKTIONS-VORGABEN:
            - datum: Leistungs- oder Rechnungsdatum strikt im Format YYYY-MM-DD. Falls kein Datum erkennbar, nutze das heutige Datum (2026-07-14).
            - aussteller: Firmenname und Markt-Standort/Adresse falls auf Beleg vorhanden (z. B. "OBI Baumarkt, Industriestr. 12, 12345 Musterstadt" oder "Hornbach").
            - bruttobetrag: Finaler Zahlbetrag inklusive Mehrwertsteuer als reine positive Zahl (z. B. 145.50).
            - uhrzeit: Lies die Uhrzeit (HH:MM) vom Beleg ab. WICHTIG für Baumarktquittungen! Falls keine Uhrzeit gefunden wird, setze einen leeren String "" ein.
            - kontoNr: Die zugewiesene Konto-Nummer ("0050", "2110", "2120", "4970", "4830", "4670" oder passend).
            - beschreibung: Kurze Zusammenfassung auf Deutsch, was gekauft wurde oder worum es geht (z. B. "Kauf von Wandfarbe und Malerzubehör").
            - wohneinheit: Zugeordnete Wohneinheit (z. B. "WE 1", "WE 2" ... "WE 7"), falls auf dem Beleg genannt, sonst "Gesamtobjekt / Allgemein".
            - mieter: Name des Mieters/Zahlers, falls auf dem Beleg oder der Überweisung genannt (z. B. "Erika Mustermann", "Hans Peter"), sonst leeres String "".
            - isEigenleistungSanierung: true, falls es sich um einen Baumarkt-Materialbeleg handelt UND das Belegdatum zwischen 2025-10-01 and 2026-01-31 liegt. Sonst false.
            - positionen: Extrahiere ALLE einzelnen Posten, Artikel oder Gebühren vom Beleg als Liste. Jede Position hat:
              * bezeichnung: Name oder Artikelbeschreibung
              * menge: Anzahl / Menge als Zahl (z. B. 1.0)
              * einzelpreis: Preis pro Stück/Einheit in EUR als Zahl
              * gesamtpreis: Gesamtpreis dieser Position in EUR als Zahl

            ANTWORTE AUSSCHLIESSLICH IM GEFORDERTEN JSON-FORMAT. ERFINDE KEINE EIGENEN KATEGORIEN!
            Verwende folgendes JSON-Format für die Antwort:
            {
              "aussteller": "Firmenname",
              "datum": "JJJJ-MM-TT",
              "uhrzeit": "HH:MM",
              "bruttobetrag": 123.45,
              "hauptkategorie": "Ausgewählte Hauptkategorie",
              "unterkategorie": "Ausgewählte Unterkategorie",
              "kontoNr": "Konto-Nr",
              "beschreibung": "Kurzbeschreibung",
              "wohneinheit": "WE 1",
              "mieter": "Erika Mustermann",
              "isEigenleistungSanierung": true/false,
              "positionen": [
                {
                  "bezeichnung": "Wandfarbe Alpina 10L",
                  "menge": 1.0,
                  "einzelpreis": 49.99,
                  "gesamtpreis": 49.99
                }
              ]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.1
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstruction)))
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            val duration = System.currentTimeMillis() - startTime
            if (jsonText != null) {
                Log.d(TAG, "--- GEMINI API CALL DIAGNOSTICS ---")
                Log.d(TAG, "Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent")
                Log.d(TAG, "Model: ${modelName}")
                Log.d(TAG, "File Type: $mimeType")
                Log.d(TAG, "File Size: $dataSize")
                Log.d(TAG, "Duration: ${duration}ms")
                Log.d(TAG, "HTTP Status: 200")
                Log.d(TAG, "Status: SUCCESS")

                // Sanitize potential markdown wrap
                val cleanedJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                
                try {
                    val adapter = moshi.adapter(ExtractedReceipt::class.java)
                    adapter.fromJson(cleanedJson) ?: throw Exception("Moshi returned null")
                } catch (pe: Exception) {
                    throw GeminiAnalysisException.InvalidResponse(pe.localizedMessage ?: "Moshi deserialization failed")
                }
            } else {
                Log.e(TAG, "--- GEMINI API CALL DIAGNOSTICS ---")
                Log.e(TAG, "Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent")
                Log.e(TAG, "Model: ${modelName}")
                Log.e(TAG, "File Type: $mimeType")
                Log.e(TAG, "File Size: $dataSize")
                Log.e(TAG, "Duration: ${duration}ms")
                Log.e(TAG, "HTTP Status: 200")
                Log.e(TAG, "Error Category: PARSING")
                Log.e(TAG, "Error Code: EMPTY_RESPONSE")
                Log.e(TAG, "Cleaned Error Message: No text in candidate response (maybe blocked by safety filters)")
                throw GeminiAnalysisException.InvalidResponse("Keine Antwort vom Modell erhalten (Sicherheitsfilter oder Blockierung).")
            }
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            Log.e(TAG, "--- GEMINI API CALL DIAGNOSTICS ---")
            Log.e(TAG, "Endpoint: POST https://generativelanguage.googleapis.com/v1beta/models/${modelName}:generateContent")
            Log.e(TAG, "Model: ${modelName}")
            Log.e(TAG, "File Type: $mimeType")
            Log.e(TAG, "File Size: $dataSize")
            Log.e(TAG, "Duration: ${duration}ms")

            when (e) {
                is GeminiAnalysisException -> throw e
                is retrofit2.HttpException -> {
                    val httpStatus = e.code()
                    val requestId = e.response()?.headers()?.get("x-goog-ext-daemon-request-id") 
                        ?: e.response()?.headers()?.get("x-goog-request-id") 
                        ?: "unknown"
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    val cleanedError = sanitizeErrorBody(errorBody)

                    Log.e(TAG, "HTTP Status: $httpStatus")
                    Log.e(TAG, "Request ID: $requestId")
                    Log.e(TAG, "Error Code: HTTP_$httpStatus")
                    Log.e(TAG, "Cleaned Error Message: $cleanedError")

                    when (httpStatus) {
                        401 -> {
                            Log.e(TAG, "Error Category: AUTH")
                            throw GeminiAnalysisException.KeyInvalid(cleanedError)
                        }
                        403 -> {
                            Log.e(TAG, "Error Category: AUTH")
                            throw GeminiAnalysisException.PermissionDenied(cleanedError)
                        }
                        429 -> {
                            Log.e(TAG, "Error Category: QUOTA")
                            throw GeminiAnalysisException.QuotaExceeded(cleanedError)
                        }
                        400 -> {
                            Log.e(TAG, "Error Category: LIMIT")
                            throw GeminiAnalysisException.RequestTooLarge(cleanedError)
                        }
                        else -> {
                            Log.e(TAG, "Error Category: SERVER")
                            throw GeminiAnalysisException.GenericError(
                                errorCode = "HTTP_$httpStatus",
                                userMessage = "Ein unerwarteter Serverfehler ist aufgetreten (HTTP $httpStatus).",
                                httpStatus = httpStatus,
                                cleanedMsg = cleanedError
                            )
                        }
                    }
                }
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "Error Category: TIMEOUT")
                    Log.e(TAG, "Error Code: TIMEOUT")
                    Log.e(TAG, "Cleaned Error Message: ${e.localizedMessage}")
                    throw GeminiAnalysisException.TimeoutError(e)
                }
                is java.io.IOException -> {
                    Log.e(TAG, "Error Category: NETWORK")
                    Log.e(TAG, "Error Code: NETWORK_ERROR")
                    Log.e(TAG, "Cleaned Error Message: ${e.localizedMessage}")
                    throw GeminiAnalysisException.NetworkError(e)
                }
                else -> {
                    Log.e(TAG, "Error Category: GENERIC")
                    Log.e(TAG, "Error Code: UNKNOWN")
                    Log.e(TAG, "Cleaned Error Message: ${e.localizedMessage}")
                    throw GeminiAnalysisException.GenericError(
                        errorCode = "UNKNOWN",
                        userMessage = "Unerwarteter Fehler: ${e.localizedMessage}",
                        cleanedMsg = e.localizedMessage
                    )
                }
            }
        }
    }


    /**
     * Estimates route distance between Start, Via (Store), and End (Property) addresses in Germany.
     */
    suspend fun estimateRouteDistance(
        startAddress: String,
        viaAddress: String,
        endAddress: String,
        routeType: String
    ): Double? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not set or is placeholder!")
            return null
        }

        val prompt = """
            Du bist ein präziser Routen- und Entfernungsrechner für Fahrtenbücher in Deutschland.
            Berechne die realistisch gefahrene Straßen-Strecke mit dem Auto in Kilometern (nur eine Zahl als Double) für folgende Adressen und Routentyp:
            - Startadresse (Meine Adresse / Wohnort): $startAddress
            - Zwischenstation (Baumarkt / Händler aus Beleg): $viaAddress
            - Zieladresse (Immobilien-Objekt): $endAddress
            - Routentyp: $routeType

            Routentyp Erklärung:
            - "standard": Rundfahrt (Startadresse -> Zwischenstation -> Zieladresse -> Startadresse)
            - "store_only": Fahrt zum Markt (Startadresse -> Zwischenstation -> Startadresse)
            - "property_only": Fahrt zum Objekt (Startadresse -> Zieladresse -> Startadresse)

            Schätze die echte Fahrtstrecke auf Straßen (keine Luftlinie).
            Gib NUR ein gültiges JSON zurück mit folgendem Aufbau (kein Markdown, kein Freitext):
            {
              "distanceKm": 24.5
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2
            )
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanedJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()
                
                val distanceRegex = """"distanceKm"\s*:\s*([0-9.]+)""".toRegex()
                val match = distanceRegex.find(cleanedJson)
                match?.groupValues?.get(1)?.toDoubleOrNull()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating route distance with Gemini: ", e)
            null
        }
    }

    /**
     * Answers natural language search queries over the Room database receipts using Gemini 3.5 Flash.
     */
    suspend fun answerNaturalLanguageQuery(
        userQuery: String,
        receipts: List<com.example.data.Receipt>
    ): AiSearchResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.e(TAG, "Gemini API Key is not set or is placeholder!")
            return null
        }

        // Build a concise summary of all receipts in Room
        val receiptsSummary = if (receipts.isEmpty()) {
            "Keine Belege in der Datenbank vorhanden."
        } else {
            receipts.joinToString(separator = "\n") { r ->
                "- ID: ${r.id}, Datum: ${r.datum}, Aussteller: '${r.aussteller}', Betrag: ${r.bruttobetrag} EUR, Kat: '${r.hauptkategorie}' / '${r.unterkategorie}', Konto: ${r.kontoNr}, WE: '${r.wohneinheit}', Mieter: '${r.mieter}', Beschr: '${r.beschreibung}', Sanierung: ${r.isEigenleistungSanierung}"
            }
        }

        val prompt = """
            Du bist ein intelligenter Finanz- und Buchhaltungs-Assistent für Immobilienverwalter und Vermieter.
            Hier ist die vollständige Liste der aktuell in der Room-Datenbank gespeicherten Belege:

            $receiptsSummary

            Frage des Nutzers: "$userQuery"

            Aufgabe:
            1. Analysiere die Belege sorgfältig hinsichtlich Datum, Aussteller, Betrag, Kategorien, Beschreibung, Wohneinheit, Mieter und Sanierung.
            2. Beantworte die Frage präzise, verständlich, freundlich und übersichtlich auf Deutsch. (Beispiel: "Im Jahr 2026 hast du insgesamt 450,00 € für Heizung ausgegeben.").
            3. Falls die Frage nach einer Summe oder bestimmten Belegen fragt, berechne die Gesamtsumme exakt und gib die IDs der maßgeblichen Belege in 'matchingReceiptIds' an.

            Gib ein gültiges JSON im folgenden Format zurück (kein Markdown-Format außerhalb):
            {
              "answer": "Deine verständliche Antwort hier...",
              "totalAmount": 450.00,
              "matchingReceiptIds": [1, 5]
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(
                responseMimeType = "application/json",
                temperature = 0.2
            )
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleanedJson = jsonText.trim()
                    .removePrefix("```json")
                    .removePrefix("```")
                    .removeSuffix("```")
                    .trim()

                val adapter = moshi.adapter(AiSearchResult::class.java)
                adapter.fromJson(cleanedJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing natural language query with Gemini: ", e)
            null
        }
    }

    // --- 1. KI STEUER- & PLAUSIBILITÄTSPRÜFER ---
    suspend fun analyzeTaxPlausibility(
        receipts: List<com.example.data.Receipt>,
        buildingPurchaseValue: Double = 600000.0
    ): TaxPlausibilityReport? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val receiptSummary = receipts.joinToString("\n") { r ->
            "- ID ${r.id}: ${r.datum}, ${r.aussteller}, ${r.bruttobetrag}€, Kat: ${r.hauptkategorie}/${r.unterkategorie}, Sanierung: ${r.isEigenleistungSanierung}, WE: ${r.wohneinheit}"
        }

        val prompt = """
            Du bist ein Steuerberater-KI-Expertensystem für deutsche Immobilienvermietung (Anlage V, 15%-Grenze § 6 Abs. 1 Nr. 1a EStG, Handwerkerleistungen § 35a EStG).
            Gebäudeanschaffungswert (ohne Grund): $buildingPurchaseValue EUR. 15%-Grenze = ${buildingPurchaseValue * 0.15} EUR netto.
            
            Prüfe die folgenden Belege auf:
            1. 15%-Grenze für anschaffungsnahe Herstellungskosten (3 Jahre ab Kauf 01.10.2025). Berechne die bisherige Gesamtsumme der Instandsetzungs-/Sanierungskosten.
            2. Plausibilität & Risiken (z.B. falsche Zuordnung von Material vs. Handwerkerleistungen, fehlende Rechnungsmerkmale, außergewöhnliche Beträge).
            3. Handlungsempfehlungen zur Optimierung der Steuererklärung.

            Belege:
            $receiptSummary

            Gib das Ergebnis im exakten JSON-Format zurück:
            {
              "overallStatus": "OK" oder "WARNUNG" oder "KRITISCH",
              "sanierungCostSum": 12500.00,
              "sanierungCostLimit": ${buildingPurchaseValue * 0.15},
              "sanierungLimitPercentage": 13.8,
              "is15PercentLimitExceeded": false,
              "findings": [
                {
                  "category": "15%-Grenze",
                  "title": "Achtung bei weiteren Sanierungen",
                  "description": "Du hast bereits 13.8% des Gebäudeanteils ausgeschöpft.",
                  "severity": "WARNUNG",
                  "actionRecommendation": "Instandhaltungen auf das 4. Jahr verschieben."
                }
              ],
              "summaryText": "Gesamtbewertung der Steuerkonformität..."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(TaxPlausibilityReport::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Tax plausibility check failed", e)
            null
        }
    }

    // --- 2. KI-MIETNEBENKOSTEN- & BETRIEBSKOSTENABRECHNUNG ---
    suspend fun generateTenantUtilityStatement(
        tenantName: String,
        unitName: String,
        sqm: Double,
        totalBuildingSqm: Double = 520.0,
        year: Int = 2025,
        receipts: List<com.example.data.Receipt>
    ): TenantUtilityStatement? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val operatingReceipts = receipts.filter { 
            it.hauptkategorie.contains("Betriebskosten", ignoreCase = true) ||
            it.hauptkategorie.contains("Laufende", ignoreCase = true)
        }.joinToString("\n") { r ->
            "- ${r.datum}: ${r.aussteller} (${r.unterkategorie}) = ${r.bruttobetrag} EUR, WE: ${r.wohneinheit}"
        }

        val prompt = """
            Erstelle eine formell korrekte deutsche Betriebskostenabrechnung (Mietnebenkostenabrechnung) für das Jahr $year.
            Mieter: $tenantName, Wohneinheit: $unitName ($sqm m² von gesamt $totalBuildingSqm m²).
            
            Umlagefähige Ausgaben im Haus:
            $operatingReceipts

            Berechne den Anteil für den Mieter nach Wohnfläche ($sqm / $totalBuildingSqm) oder direkt zugeordneten Kosten.
            Ggf. angenommene Vorauszahlungen des Mieters: 1800.00 EUR.

            Erstelle ein exaktes JSON im Format:
            {
              "tenantName": "$tenantName",
              "unitName": "$unitName",
              "period": "01.01.$year - 31.12.$year",
              "totalOperatingCosts": 12400.00,
              "tenantShareAmount": 1950.00,
              "prepaymentsAmount": 1800.00,
              "balanceAmount": 150.00,
              "costBreakdown": [
                {
                  "category": "Grundsteuer & Gebäudeversicherung",
                  "totalBuildingCost": 3200.00,
                  "costKey": "Wohnfläche ($sqm m² / $totalBuildingSqm m²)",
                  "tenantShare": 492.30
                }
              ],
              "formalDraftText": "Sehr geehrte/r $tenantName,\n\nhiermit erhalten Sie Ihre Betriebskostenabrechnung für das Jahr $year..."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(TenantUtilityStatement::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Utility statement generation failed", e)
            null
        }
    }

    // --- 3. KI-MIETPREIS- & RENDITE-OPTIMIERER ---
    suspend fun optimizeRentAndYield(
        propertyLocation: String,
        currentUnitsInfo: String,
        receipts: List<com.example.data.Receipt>
    ): RentYieldOptimizationReport? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val prompt = """
            Du bist ein Experte für deutsches Mietrecht (BGB § 558, § 559 Modernisierungsumlage, Indexmiete) und Immobilien-Renditeoptimierung.
            Standort des 7-Familienhauses: $propertyLocation.
            
            Aktuelle Mieteinheiten-Übersicht:
            $currentUnitsInfo

            Aufgabe:
            1. Analysiere das Mietsteigerungs- und Renditepotenzial für jede Wohneinheit unter Berücksichtigung von ortsüblicher Vergleichsmiete und Modernisierungen.
            2. Berechne die Ist- und Soll-Kaltmiete sowie die Brutto-Rendite.
            3. Gib konkrete gesetzlich fundierte Handlungsempfehlungen.

            Antworte im exakten JSON-Format:
            {
              "currentTotalRentNet": 4200.00,
              "recommendedTotalRentNet": 4850.00,
              "currentGrossYieldPct": 5.2,
              "potentialGrossYieldPct": 6.1,
              "unitOptimizations": [
                {
                  "unitName": "WE 01 (OG Links)",
                  "currentRent": 650.00,
                  "suggestedRent": 740.00,
                  "reasoning": "Modernisierung Bad & Bodenbelag durchgeführt.",
                  "legalBasis": "§ 559 BGB (8% Modernisierungsumlage) bzw. Anpassung an Mietspiegel"
                }
              ],
              "summaryText": "Strategie-Zusammenfassung zur Mietpreisoptimierung..."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(RentYieldOptimizationReport::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Rent optimization failed", e)
            null
        }
    }

    // --- 4. KI-MÄNGEL- & SCHADENS-ASSISTENT MIT FOTO-ANALYSE ---
    suspend fun assessDamagePhoto(
        bitmap: Bitmap,
        userDescription: String = ""
    ): DamageAssessmentResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val parts = mutableListOf<Part>()
        val base64Image = bitmap.toBase64()
        parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
        
        val promptText = """
            Analysiere dieses Schadensbild aus einer Mietwohnung / Immobilie.
            Zusätzliche Nutzerbeschreibung: "$userDescription"

            Aufgabe:
            1. Identifiziere die Schadensart (z.B. Schimmelbefall, Rohrbruch, Wasserschaden, Fliesenriss, Fensterschaden).
            2. Schweregrad & Dringlichkeit einschätzen.
            3. Steuerliche Zuordnung (Sofort abzugsfähiger Erhaltungsaufwand vs. Herstellungskosten).
            4. Geschätzte Reparaturkosten.
            5. Erstelle ein fertiges Handwerker-Angebotsanfrage-Schreiben oder eine Mieter-Information.

            Antworte im exakten JSON-Format:
            {
              "damageTitle": "Wasserschaden an Decke / Schimmelbildung",
              "severity": "Hoch",
              "taxCategory": "Sofort abzugsfähiger Erhaltungsaufwand (Konto 4830)",
              "estimatedRepairCostRange": "350 € - 800 €",
              "urgency": "Dringend (innerhalb 48h)",
              "recommendedAction": "Maler & Leckortung beauftragen, Trocknungsgerät aufstellen.",
              "craftsmanDraftLetter": "Sehr geehrte Damen und Herren,\n\nin unserer Mietwohnung liegt folgender Schaden vor..."
            }
        """.trimIndent()
        parts.add(Part(text = promptText))

        val request = GeminiRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(DamageAssessmentResult::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Damage photo assessment failed", e)
            null
        }
    }

    // --- 5. KI-VERTRAGS- & FRISTEN-ANALYSATOR ---
    suspend fun analyzeContractDocument(
        bitmap: Bitmap? = null,
        textContent: String? = null
    ): ContractAnalysisResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val parts = mutableListOf<Part>()
        if (bitmap != null) {
            val base64Image = bitmap.toBase64()
            parts.add(Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image)))
        }
        
        val promptText = """
            Analysiere diesen Vertrag (z.B. Wohnraum-Mietvertrag, Versicherungsvertrag, Dienstleister- oder Handwerkervertrag).
            Zusätzlicher Text: "${textContent ?: ""}"

            Extrahiere die rechtlichen & finanziellen Eckpunkte:
            1. Vertragstyp & Vertragsparteien
            2. Beginn, Laufzeit & Kündigungsfristen
            3. Mietanpassungsklausel (Index, Staffelmiete, BGB)
            4. Kaution & Schonheitsreparaturen
            5. Risikopunkte & rechtlich unzulässige Klauseln (z.B. Starre Schönheitsreparaturklauseln)

            Antworte im exakten JSON-Format:
            {
              "contractType": "Wohnraum-Mietvertrag",
              "parties": "Vermieter: ImmoGmbH • Mieter: Max Mustermann",
              "startDate": "01.04.2024",
              "noticePeriod": "3 Monate zum Monatsende",
              "rentAdjustmentClause": "Indexmiete nach Verbraucherpreisindex",
              "depositTerms": "3 Monatskaltmieten (1.950 €) auf Kautionskonto",
              "importantClauses": ["Kleinreparaturklausel bis 100€/Einzelfall", "Tierhaltung nach Zustimmung"],
              "riskFlags": ["Alte Schönheitsreparaturklausel könnte laut BGH-Rechtsprechung unwirksam sein."],
              "summary": "Standard-Mietvertrag mit wirksamer Indexmietklausel."
            }
        """.trimIndent()
        parts.add(Part(text = promptText))

        val request = GeminiRequest(
            contents = listOf(Content(parts = parts)),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(ContractAnalysisResult::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Contract analysis failed", e)
            null
        }
    }

    // --- 6. KI KONTOAUSZUGS-MATCHING (BANKABGLEICH) ---
    suspend fun matchBankStatement(
        rawStatementText: String,
        receipts: List<com.example.data.Receipt>,
        tenantsInfo: String = ""
    ): BankStatementReconciliationResult? {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return null

        val receiptsSummary = receipts.joinToString("\n") { r ->
            "Beleg ID ${r.id}: ${r.datum} | ${r.aussteller} | ${r.bruttobetrag} € | Kat: ${r.hauptkategorie}/${r.unterkategorie} | WE: ${r.wohneinheit}"
        }

        val prompt = """
            Du bist ein automatischer Buchhaltungs- & Bankabgleich-Assistent für Vermieter.
            Vergleiche die folgende Transaktionsliste / den Kontoauszug mit den gespeicherten Belegen und den Mieterdaten.

            Gespeicherte Belege in der App:
            $receiptsSummary

            Mieter- & Sollliste:
            $tenantsInfo

            Kontoauszug / Transaktionsliste:
            $rawStatementText

            Aufgaben:
            1. Ordne Ausgaben (negativ) Belegen zu. Wenn für eine Ausgabe KEIN Beleg existiert, markiere mit status="MISSING_RECEIPT".
            2. Ordne Einnahmen (positiv) Mietern zu. Wenn eine Miete fehlt oder zu gering ist, markiere mit status="RENT_ARREARS".
            3. Bei erfolgreicher Zuordnung markiere status="MATCHED". Bei unklaren Transaktionen status="UNMATCHED".

            Gib das Ergebnis im exakten JSON-Format zurück:
            {
              "period": "01.07.2025 - 31.07.2025",
              "totalIncoming": 4250.00,
              "totalOutgoing": 1840.50,
              "matchedCount": 8,
              "missingReceiptsCount": 2,
              "rentArrearsCount": 1,
              "items": [
                {
                  "date": "02.07.2025",
                  "counterparty": "Max Mustermann",
                  "amount": 750.00,
                  "isIncome": true,
                  "purpose": "Miete Juli WE 01",
                  "matchedReceiptId": null,
                  "matchedTenantName": "Max Mustermann",
                  "status": "MATCHED",
                  "notes": "Miete vollständig eingegangen"
                },
                {
                  "date": "15.07.2025",
                  "counterparty": "Hornbach Baumarkt",
                  "amount": 145.80,
                  "isIncome": false,
                  "purpose": "Farbe & Pinselsatz",
                  "matchedReceiptId": null,
                  "matchedTenantName": null,
                  "status": "MISSING_RECEIPT",
                  "notes": "Kein passender Beleg in der App gefunden! Bitte Beleg hochladen."
                }
              ],
              "summary": "Zusammenfassender Bericht zum Kontoauszug..."
            }
        """.trimIndent()

        val request = GeminiRequest(
            contents = listOf(Content(parts = listOf(Part(text = prompt)))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2)
        )

        return try {
            val response = generateContentWithRetry(apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonText != null) {
                val cleaned = jsonText.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                moshi.adapter(BankStatementReconciliationResult::class.java).fromJson(cleaned)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "Bank statement matching failed", e)
            null
        }
    }
}
