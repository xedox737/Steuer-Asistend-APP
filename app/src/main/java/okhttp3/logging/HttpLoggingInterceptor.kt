package okhttp3.logging

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Privacy-safe compatibility replacement for OkHttp's verbose logging interceptor.
 *
 * The app may configure BODY logging at call sites, but this implementation never logs
 * request or response content. It deliberately leaves requests untouched; validation and
 * prompt safety belong to the API layer rather than a transport interceptor.
 */
class HttpLoggingInterceptor : Interceptor {
    enum class Level {
        NONE,
        BASIC,
        HEADERS,
        BODY
    }

    var level: Level = Level.NONE

    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request())
}
