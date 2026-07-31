package com.example.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataDuplicateCleanupTest {
    @Test
    fun plannerKeepsIndexReferencedMetadataAndTargetsOnlyOrphans() {
        val plan = MetadataDuplicateCleanupPlanner.plan(group())

        assertEquals("active", plan.activeMetadataFileId)
        assertEquals(listOf("orphan-a", "orphan-b"), plan.orphanMetadataFileIds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsGroupWithoutIndexReference() {
        MetadataDuplicateCleanupPlanner.plan(group(referencedId = null))
    }

    @Test(expected = IllegalArgumentException::class)
    fun plannerRejectsAmbiguousIndexReferences() {
        MetadataDuplicateCleanupPlanner.plan(
            group().copy(
                referencedFileIdInIndex = null,
                referencedMetadataFileIds = setOf("active", "orphan-a")
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cleanupRequiresExplicitConfirmation() {
        runBlocking {
            MetadataDuplicateCleanupExecutor(MetadataDuplicateFileDeleter { true })
                .execute(MetadataDuplicateCleanupPlanner.plan(group()), explicitlyConfirmed = false)
        }
    }

    @Test
    fun cleanupNeverDeletesActiveMetadataFile() = runBlocking {
        val calls = mutableListOf<String>()
        val executor = MetadataDuplicateCleanupExecutor(
            MetadataDuplicateFileDeleter {
                calls += it
                true
            }
        )

        val result = executor.execute(
            MetadataDuplicateCleanupPlanner.plan(group()),
            explicitlyConfirmed = true
        )

        assertTrue(result.completed)
        assertEquals(listOf("orphan-a", "orphan-b"), calls)
        assertFalse(calls.contains("active"))
    }

    @Test
    fun cleanupReportsPartialFailureAndWritesAudit() = runBlocking {
        var audit: MetadataDuplicateCleanupResult? = null
        val executor = MetadataDuplicateCleanupExecutor(
            deleter = MetadataDuplicateFileDeleter { it != "orphan-b" },
            audit = { audit = it }
        )

        val result = executor.execute(
            MetadataDuplicateCleanupPlanner.plan(group()),
            explicitlyConfirmed = true
        )

        assertFalse(result.completed)
        assertEquals(listOf("orphan-a"), result.deletedMetadataFileIds)
        assertEquals(1, result.failures.size)
        assertEquals(result, audit)
    }

    private fun group(referencedId: String? = "active") = MetadataDuplicateGroup(
        internalId = "receipt-1",
        files = listOf(
            detail("orphan-b", false),
            detail("active", referencedId == "active"),
            detail("orphan-a", false)
        ),
        referencedFileIdInIndex = referencedId
    )

    private fun detail(id: String, referenced: Boolean) = MetadataFileDetails(
        driveId = id,
        name = "$id.json",
        createdTime = "2026-01-01T00:00:00Z",
        modifiedTime = "2026-01-01T00:00:00Z",
        isReferencedInIndex = referenced
    )
}
