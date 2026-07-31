package com.example.data

import java.io.IOException
import java.net.SocketTimeoutException

/** Central policy for classifying Drive sync failures without losing local data. */
object DriveSyncFailurePolicy {
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
