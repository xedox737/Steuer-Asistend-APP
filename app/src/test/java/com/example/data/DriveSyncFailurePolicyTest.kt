package com.example.data

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSyncFailurePolicyTest {
    @Test
    fun timeoutIsRetryable() =
        assertTrue(DriveSyncFailurePolicy.isRetryable(SocketTimeoutException("timeout")))

    @Test
    fun ioFailureIsRetryable() =
        assertTrue(DriveSyncFailurePolicy.isRetryable(IOException("offline")))

    @Test
    fun programmingFailureIsNotRetryable() =
        assertFalse(DriveSyncFailurePolicy.isRetryable(IllegalStateException("invalid state")))

    @Test
    fun retryDelayUsesBoundedExponentialBackoff() {
        assertEquals(1_000L, DriveSyncFailurePolicy.retryDelayMillis(0))
        assertEquals(4_000L, DriveSyncFailurePolicy.retryDelayMillis(2))
        assertEquals(32_000L, DriveSyncFailurePolicy.retryDelayMillis(99))
    }

    @Test
    fun authFailureGetsActionableMessage() {
        assertTrue(
            DriveSyncFailurePolicy.userMessage(RuntimeException("HTTP 403"))
                .contains("Anmeldung")
        )
    }

    @Test
    fun offlineMessageConfirmsLocalDataRetention() {
        assertTrue(
            DriveSyncFailurePolicy.userMessage(IOException("offline"))
                .contains("lokal gespeichert")
        )
    }
}
