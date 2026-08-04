package com.example.api

object GeminiRetryPolicy {
    const val maxRetries: Int = 2
    private const val initialDelayMs: Long = 500L

    fun shouldRetry(httpStatus: Int, retriesCompleted: Int): Boolean {
        return httpStatus in setOf(429, 503) && retriesCompleted < maxRetries
    }

    fun delayMillis(retriesCompleted: Int): Long {
        require(retriesCompleted >= 0)
        return initialDelayMs * (1L shl retriesCompleted.coerceAtMost(10))
    }
}

object GeminiReceiptSizePolicy {
    const val maxPages: Int = 20
    const val maxImageBytes: Int = 20 * 1024 * 1024

    fun isAllowed(pageCount: Int, totalImageBytes: Long): Boolean {
        return pageCount in 0..maxPages && totalImageBytes in 0..maxImageBytes.toLong()
    }
}
