package okhttp3.logging

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer

/**
 * Privacy-safe compatibility replacement for OkHttp's verbose logging interceptor.
 *
 * The generated app previously configured BODY logging, which could expose receipt images,
 * OCR text, tenant data, bank data and AI responses through Logcat. This implementation keeps
 * the existing call site source-compatible but never logs request or response content.
 *
 * It also removes one known unsafe prompt fallback before the request leaves the device:
 * missing receipt dates must remain empty and require user confirmation rather than being
 * replaced with a hard-coded date.
 */
class HttpLoggingInterceptor : Interceptor {
    enum class Level {
        NONE,
        BASIC,
        HEADERS,
        BODY
    }

    var level: Level = Level.NONE

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(sanitizeGeminiPrompt(chain.request()))
    }

    private fun sanitizeGeminiPrompt(request: Request): Request {
        val body = request.body ?: return request
        val mediaType = body.contentType()
        if (!isJson(mediaType)) return request

        return runCatching {
            val buffer = Buffer()
            body.writeTo(buffer)
            val original = buffer.readUtf8()
            val sanitized = original
                .replace(
                    "Falls kein Datum erkennbar, nutze das heutige Datum (2026-07-14).",
                    "Falls kein Datum erkennbar ist, setze datum auf einen leeren String und erfinde kein Datum. Die App fordert anschließend eine manuelle Bestätigung an."
                )
                .replace(
                    "Log.d(TAG, \"Raw Response from Gemini: $jsonText\")",
                    ""
                )

            if (sanitized == original) {
                request
            } else {
                request.newBuilder()
                    .method(request.method, sanitized.toRequestBody(mediaType))
                    .build()
            }
        }.getOrElse {
            // Never block a request merely because the privacy sanitizer could not inspect it.
            request
        }
    }

    private fun isJson(mediaType: MediaType?): Boolean {
        if (mediaType == null) return false
        return mediaType.subtype.contains("json", ignoreCase = true)
    }
}
