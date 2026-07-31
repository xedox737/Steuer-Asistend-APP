package com.example.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-flight boundary for the complete receipt Drive transaction.
 *
 * The guarded block must include folder resolution, original document upsert, metadata upsert,
 * immediate local Drive-ID persistence and receipt-index update.
 */
class ReceiptSyncGate {
    private val mutex = Mutex()

    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock {
        block()
    }
}
