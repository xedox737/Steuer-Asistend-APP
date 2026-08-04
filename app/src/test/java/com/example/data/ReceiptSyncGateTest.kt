package com.example.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptSyncGateTest {
    @Test
    fun fiveParallelSyncsCreateExactlyOneRemoteIdentity() = runBlocking {
        val gate = ReceiptSyncGate()
        val createCount = AtomicInteger(0)
        var remoteId: String? = null

        val results = (1..5).map {
            async {
                gate.run {
                    val existing = remoteId
                    if (existing != null) {
                        existing
                    } else {
                        delay(10)
                        createCount.incrementAndGet()
                        "drive-main-1".also { remoteId = it }
                    }
                }
            }
        }.awaitAll()

        assertEquals(1, createCount.get())
        assertEquals(listOf("drive-main-1"), results.distinct())
    }
    @Test
    fun retryAfterAbortReusesRemoteIdentityInsteadOfCreatingAgain() = runBlocking {
        val gate = ReceiptSyncGate()
        val createCount = AtomicInteger(0)
        var remoteId: String? = null

        runCatching {
            gate.run {
                if (remoteId == null) {
                    createCount.incrementAndGet()
                    remoteId = "drive-main-1"
                }
                error("simulated app interruption after remote create")
            }
        }

        val retriedId = gate.run {
            remoteId ?: "drive-main-2".also {
                createCount.incrementAndGet()
                remoteId = it
            }
        }

        assertEquals("drive-main-1", retriedId)
        assertEquals(1, createCount.get())
    }

}
