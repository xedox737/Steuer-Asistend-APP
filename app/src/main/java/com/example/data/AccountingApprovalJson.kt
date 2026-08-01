package com.example.data

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Lossless local storage for user-confirmed accounting allocations and booking proposals.
 * Empty or malformed legacy values are read as empty lists and never converted into approvals.
 */
object AccountingApprovalJson {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val allocationAdapter = moshi.adapter<List<PersistedAllocation>>(
        Types.newParameterizedType(List::class.java, PersistedAllocation::class.java)
    )

    private val proposalAdapter = moshi.adapter<List<PersistedBookingProposal>>(
        Types.newParameterizedType(List::class.java, PersistedBookingProposal::class.java)
    )

    fun encodeAllocations(values: List<PersistedAllocation>): String =
        allocationAdapter.toJson(values)

    fun decodeAllocations(json: String?): List<PersistedAllocation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { allocationAdapter.fromJson(json).orEmpty() }
            .getOrDefault(emptyList())
    }

    fun encodeBookingProposals(values: List<PersistedBookingProposal>): String =
        proposalAdapter.toJson(values)

    fun decodeBookingProposals(json: String?): List<PersistedBookingProposal> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching { proposalAdapter.fromJson(json).orEmpty() }
            .getOrDefault(emptyList())
    }
}
