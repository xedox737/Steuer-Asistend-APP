package com.example.data

import java.io.IOException
import java.net.SocketTimeoutException

/** Central policy for classifying Drive sync failures without losing local data. */
object DriveSyncFailurePolicy {
    const val MAX_READ_RETRIES = 1
    const val MAX_READ_RETRY_DELAY_MS = 2_000L

    /** Only the caller's explicitly idempotent GET may use this policy. */
    fun retryableRead(error: Throwable): Boolean = error is IOException &&
        error !is com.example.api.DriveAccessFailure && error !is javax.net.ssl.SSLException && error !is java.net.ProtocolException

    fun retryableReadStatus(status: Int): Boolean = status in setOf(408, 429, 500, 502, 503, 504)

    /** A long Retry-After stops this run instead of retrying earlier than the server permits. */
    fun readRetryDelayMillis(retry: Int, retryAfter: String? = null, nowMillis: Long = System.currentTimeMillis(),
                             jitterMillis: Long = kotlin.random.Random.nextLong(0, 126)): Long? {
        if (retry !in 0 until MAX_READ_RETRIES) return null
        val requested = retryAfter?.let { value ->
            value.trim().toLongOrNull()?.let { seconds ->
                if (seconds < 0 || seconds > MAX_READ_RETRY_DELAY_MS / 1000) return null
                seconds * 1000
            } ?: runCatching {
                (java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant().toEpochMilli() - nowMillis).coerceAtLeast(0)
            }.getOrNull()
        }
        if (requested != null && requested > MAX_READ_RETRY_DELAY_MS) return null
        return maxOf((250L shl retry) + jitterMillis.coerceIn(0, 125), requested ?: 0)
    }

    fun isRetryable(throwable: Throwable): Boolean = when (throwable) {
        is SocketTimeoutException, is IOException -> true
        else -> false
    }

    fun retryDelayMillis(attempt: Int): Long {
        val safeAttempt = attempt.coerceIn(0, 5)
        return (1_000L shl safeAttempt).coerceAtMost(32_000L)
    }

    fun userMessage(throwable: Throwable): String = when {
        throwable is SocketTimeoutException ->
            "Zeitüberschreitung bei Google Drive. Die Synchronisierung kann erneut versucht werden."
        throwable is IOException ->
            "Google Drive ist momentan nicht erreichbar. Die Daten bleiben lokal gespeichert."
        throwable.message?.contains("401") == true || throwable.message?.contains("403") == true ->
            "Die Google-Drive-Anmeldung ist abgelaufen oder nicht ausreichend berechtigt."
        else ->
            "Die Google-Drive-Synchronisierung ist fehlgeschlagen. Die lokalen Daten bleiben erhalten."
    }
}
