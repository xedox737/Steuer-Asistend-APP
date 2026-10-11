package com.example.api

import com.squareup.moshi.Moshi

/**
 * Classifies Gemini HTTP errors without treating every HTTP 400 as an oversized document.
 * Only known backend codes/reasons and narrowly defined message fallbacks are considered.
 * The raw response and credentials are never retained in diagnostics.
 */
object GeminiHttpErrorClassifier {
    private val adapter = Moshi.Builder().build().adapter(Map::class.java)
    private val keyPattern = Regex("AIzaSy[A-Za-z0-9_-]{20,}")
    private val keyFieldPattern = Regex("(?i)(api[_ -]?key\\s*[:=]\\s*)[^\\s,;\\\"}]+")
    private val bearerPattern = Regex("(?i)Bearer\\s+[^\\s\\\"]+")
    private val base64Pattern = Regex("[A-Za-z0-9+/]{160,}={0,2}")

    fun sanitizeErrorBody(body: String?, apiKey: String? = null): String {
        if (body.isNullOrBlank()) return ""
        // Never persist the complete provider payload. Retain only bounded structured diagnostics.
        val error = parseError(body) ?: return "Gemini error response (unstructured)"
        val status = (error["status"] as? String).orEmpty().take(60)
        val code = (error["code"] as? Number)?.toInt()?.toString().orEmpty()
        val reasons = details(error).mapNotNull { it["reason"] as? String }
            .filter { it.matches(Regex("[A-Za-z_]{1,80}")) }.distinct().take(5)
        val diagnostic = "code=$code status=$status reasons=${reasons.joinToString(",")}"
        var clean = diagnostic.replace(keyPattern, "[REDACTED_KEY]")
            .replace(keyFieldPattern, "$1[REDACTED_KEY]")
            .replace(bearerPattern, "Bearer [REDACTED]")
            .replace(base64Pattern, "[REDACTED_DATA]")
        if (!apiKey.isNullOrEmpty()) clean = clean.replace(apiKey, "[REDACTED_KEY]")
        return clean.take(400)
    }

    fun classify(httpStatus: Int, errorBody: String?, apiKey: String? = null): GeminiAnalysisException {
        val error = parseError(errorBody)
        val status = (error?.get("status") as? String).orEmpty().uppercase()
        val reasons = error?.let(::details).orEmpty()
            .mapNotNull { it["reason"] as? String }.map { it.uppercase() }
        val message = (error?.get("message") as? String).orEmpty()
        val diagnostic = sanitizeErrorBody(errorBody, apiKey)
        if (httpStatus == 401) return GeminiAnalysisException.KeyInvalid(diagnostic)
        if (httpStatus == 403) return GeminiAnalysisException.PermissionDenied(diagnostic)
        if (httpStatus == 429) return GeminiAnalysisException.QuotaExceeded(diagnostic)
        if (httpStatus == 400) {
            val keyReason = reasons.any { it in setOf("API_KEY_INVALID", "API_KEY_NOT_VALID", "INVALID_API_KEY", "API_KEY_EXPIRED") }
            val permissionReason = reasons.any { it in setOf("API_KEY_SERVICE_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED", "ACCESS_TOKEN_SCOPE_INSUFFICIENT") }
            val keyMessage = Regex("(?i)\\b(api[ -]?key|apikey)\\b.{0,60}\\b(invalid|not valid|expired|rejected)\\b|\\b(invalid|expired)\\b.{0,35}\\bapi[ -]?key\\b")
            val sizeMessage = Regex("(?i)(request payload|payload size|request size|input token|too many tokens|maximum request size|request body).{0,80}(too large|exceed|limit|maximum)|(?:too large|exceed).{0,60}(?:payload|request size|token limit)")
            val sizeReason = reasons.any { it in setOf("REQUEST_TOO_LARGE", "PAYLOAD_TOO_LARGE", "REQUEST_SIZE_EXCEEDED", "CONTENT_TOO_LARGE") }
            return when {
                keyReason || keyMessage.containsMatchIn(message) -> GeminiAnalysisException.KeyInvalid(diagnostic)
                permissionReason || status == "PERMISSION_DENIED" -> GeminiAnalysisException.PermissionDenied(diagnostic)
                sizeReason || sizeMessage.containsMatchIn(message) -> GeminiAnalysisException.RequestTooLarge(diagnostic)
                else -> GeminiAnalysisException.GenericError(
                    "INVALID_REQUEST",
                    "Die Gemini-Anfrage konnte nicht verarbeitet werden. Bitte erneut versuchen. Falls der Fehler bleibt, Gemini-Einstellungen prüfen.",
                    400,
                    diagnostic
                )
            }
        }
        return GeminiAnalysisException.GenericError(
            "HTTP_$httpStatus",
            "Ein unerwarteter Serverfehler ist aufgetreten (HTTP $httpStatus).",
            httpStatus,
            diagnostic
        )
    }

    private fun parseError(body: String?): Map<*, *>? = try {
        if (body.isNullOrBlank()) null
        else (adapter.fromJson(body) as? Map<*, *>)?.get("error") as? Map<*, *>
    } catch (_: Exception) {
        null
    }

    private fun details(error: Map<*, *>): List<Map<*, *>> =
        (error["details"] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
}
