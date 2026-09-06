package com.example.data

import com.example.api.ManagedDocumentAiField
import com.example.api.ManagedDocumentAiResult
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SyntheticDocumentWorkflowTest {
    @Test fun `all 35 fictitious files are materialized in an isolated temporary directory`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val root = Files.createTempDirectory("steuer-assistent-synthetic-").toFile()
        val files = SyntheticDocumentFixtureFactory.materialize(root, fixtures)

        assertEquals((1..35).toList(), fixtures.map { it.number })
        assertEquals(35, files.size)
        assertTrue(files.values.all { it.isFile && it.length() > 0L && it.canonicalPath.startsWith(root.canonicalPath) })
        assertEquals(fixtures[0].sha256, fixtures[18].sha256)
        assertNotEquals(fixtures[0].sha256, fixtures[19].sha256)
        assertEquals(fixtures[0].filename, fixtures[19].filename)
        assertEquals(fixtures[0].sha256, fixtures[34].sha256)
        assertFalse(fixtures.joinToString { it.bytes.toString(Charsets.ISO_8859_1) }.contains("IBAN", ignoreCase = true))
    }

    @Test fun `embedded text is preferred while image pdf remains an OCR candidate without changing originals`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val embedded = fixtures.single { it.number == 7 }
        val imagePdf = fixtures.single { it.number == 8 }
        val beforeEmbedded = embedded.sha256
        val beforeImagePdf = imagePdf.sha256

        val embeddedText = PdfEmbeddedTextExtractor.extract(embedded.bytes)
        val imagePdfText = PdfEmbeddedTextExtractor.extract(imagePdf.bytes)

        assertTrue(PdfEmbeddedTextExtractor.isUsable(embeddedText))
        assertTrue(embeddedText.contains("Rechnungsnummer EMB-2026-4711"))
        assertEquals("", imagePdfText)
        assertFalse(PdfEmbeddedTextExtractor.isUsable(imagePdfText))
        assertEquals(beforeEmbedded, embedded.sha256)
        assertEquals(beforeImagePdf, imagePdf.sha256)
    }

    @Test fun `deterministic AI suggestions cover core types and stay nonbinding until field confirmation`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val fakeAi = FixtureAiProvider()
        val expected = mapOf(
            9 to ManagedDocumentType.KAUFVERTRAG,
            10 to ManagedDocumentType.DARLEHENSVERTRAG,
            12 to ManagedDocumentType.ENERGIEAUSWEIS,
            13 to ManagedDocumentType.MIETVERTRAG,
            2 to ManagedDocumentType.RECHNUNG,
            34 to ManagedDocumentType.SONSTIGES
        )
        expected.forEach { (number, type) -> assertEquals(type.name, fakeAi.analyze(fixtures.single { it.number == number }).documentType) }

        val current = mapOf("kaufpreis" to "510000", "adresse" to "Bestehender Testwert")
        val proposals = fakeAi.analyze(fixtures.single { it.number == 9 }).reviewFields(current)
        assertTrue(proposals.all { it.decision == DocumentFieldDecision.AUSSTEHEND })
        assertTrue(DocumentReviewPolicy.confirmedValues(proposals).isEmpty())
        assertEquals("510000", proposals.single { it.key == "kaufpreis" }.currentValue)

        val decisions = proposals.map {
            when (it.key) {
                "kaufpreis" -> it.copy(decision = DocumentFieldDecision.UEBERNEHMEN, editedValue = "515000")
                else -> it.copy(decision = DocumentFieldDecision.IGNORIEREN)
            }
        }
        assertEquals(mapOf("kaufpreis" to "515000"), DocumentReviewPolicy.confirmedValues(decisions))
        assertEquals("Bestehender Testwert", current.getValue("adresse"))
        assertTrue(fakeAi.analyze(fixtures.single { it.number == 34 }).confidence < 0.5)
    }

    @Test fun `confirmed reclassification moves and renames same fake Drive original for three types`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val fakeDrive = InMemoryDocumentDrive()
        listOf(9, 10, 13).forEach { number ->
            val fixture = fixtures.single { it.number == number }
            val original = fakeDrive.upload(fixture, parentId = "folder-sonstiges")
            val before = fakeDrive.snapshot()

            // A suggestion alone is deliberately side-effect free.
            assertEquals(before, fakeDrive.snapshot())

            val route = targetRoute(fixture)
            val targetFolder = route.canonicalKey
            val targetName = DocumentFilenameGenerator.document(
                fixture.expectedType, documentDate(fixture), fixture.expectedType.name,
                fixture.filename.substringAfterLast('.', "pdf"), "synthetic-$number"
            )
            val result = fakeDrive.reorganize(original.id, targetFolder, targetName, fixture.sha256)

            assertTrue(result.canApply)
            assertEquals(original.id, result.file.id)
            assertEquals(fixture.sha256, result.file.sha256)
            assertEquals(targetFolder, result.file.parentId)
            assertEquals(targetName, result.file.name)
            assertTrue(route.displayPath.endsWith(fixture.expectedTargetSuffix))
            assertEquals("SYNCED", result.status)
        }
    }

    @Test fun `offline confirmed change stays pending and resumes without identity or hash changes`() {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 9 }
        val fakeDrive = InMemoryDocumentDrive()
        val original = fakeDrive.upload(fixture, "folder-sonstiges")
        fakeDrive.available = false
        val offline = fakeDrive.reorganize(original.id, "property:p/00_stammdaten/01_kauf_eigentum", "Kaufvertrag.pdf", fixture.sha256)
        assertFalse(offline.canApply)
        assertEquals("DRIVE_REORGANIZATION_PENDING", offline.status)
        assertEquals(original, fakeDrive.file(original.id))

        fakeDrive.available = true
        val online = fakeDrive.reorganize(original.id, "property:p/00_stammdaten/01_kauf_eigentum", "Kaufvertrag.pdf", fixture.sha256)
        assertTrue(online.canApply)
        assertEquals(original.id, online.file.id)
        assertEquals(original.sha256, online.file.sha256)
    }

    @Test fun `read only inventory detects legacy orphans conflicts duplicates and missing references`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val files = fixtures.map { fixture ->
            DriveInventoryFile(
                driveFileId = fixture.driveFileId,
                name = fixture.filename,
                parentIds = listOf(fixture.legacyPath.ifBlank { "current" }),
                sizeBytes = fixture.bytes.size.toLong(),
                sha256 = fixture.declaredSha256 ?: fixture.sha256,
                receiptInternalId = fixture.receiptInternalId,
                documentId = fixture.documentId,
                appRelevant = fixture.appRelevant && fixture.legacyPath.isBlank(),
                legacyFolder = fixture.legacyPath.isNotBlank(),
                reachable = fixture.reachable
            )
        }
        val references = listOf(
            DriveInventoryReference(fixtures[0].driveFileId, "receipt-01", source = DriveDocumentInventorySource.LOCAL_RECEIPT, targetFilename = fixtures[0].filename),
            DriveInventoryReference(fixtures[0].driveFileId, "receipt-01", source = DriveDocumentInventorySource.RECEIPT_INDEX, targetFilename = fixtures[0].filename),
            DriveInventoryReference(fixtures[1].driveFileId, "receipt-02", source = DriveDocumentInventorySource.LOCAL_RECEIPT, targetFilename = fixtures[1].filename),
            DriveInventoryReference(fixtures[1].driveFileId, "receipt-02", source = DriveDocumentInventorySource.RECEIPT_INDEX, targetFilename = fixtures[1].filename),
            DriveInventoryReference(fixtures[2].driveFileId, "receipt-03", source = DriveDocumentInventorySource.LOCAL_RECEIPT, targetFilename = fixtures[2].filename),
            DriveInventoryReference("shared-drive-25", "receipt-a", source = DriveDocumentInventorySource.LOCAL_RECEIPT),
            DriveInventoryReference("shared-drive-25", "receipt-b", source = DriveDocumentInventorySource.RECEIPT_INDEX),
            DriveInventoryReference(fixtures[25].driveFileId, "shared-receipt", source = DriveDocumentInventorySource.LOCAL_RECEIPT),
            DriveInventoryReference("second-drive-same-receipt", "shared-receipt", source = DriveDocumentInventorySource.RECEIPT_INDEX),
            DriveInventoryReference(fixtures[26].driveFileId, documentId = "shared-document", source = DriveDocumentInventorySource.MANAGED_DOCUMENT),
            DriveInventoryReference("second-drive-same-document", documentId = "shared-document", source = DriveDocumentInventorySource.DOCUMENT_INDEX),
            DriveInventoryReference(fixtures[30].driveFileId, "legacy-receipt-31", source = DriveDocumentInventorySource.LOCAL_RECEIPT, targetFilename = "new-name.pdf"),
            DriveInventoryReference(fixtures[30].driveFileId, "legacy-receipt-31", source = DriveDocumentInventorySource.RECEIPT_INDEX, targetFilename = "new-name.pdf"),
            DriveInventoryReference(fixtures[31].driveFileId, "legacy-index-32", source = DriveDocumentInventorySource.RECEIPT_INDEX)
        )
        val beforeFiles = files.map { it.copy(parentIds = it.parentIds.toList()) }
        val beforeReferences = references.toList()
        val result = DriveDocumentInventoryPlanner.build(files, references)

        assertEquals(beforeFiles, files)
        assertEquals(beforeReferences, references)
        assertNull(result.find { it.driveFileId == fixtures[29].driveFileId })
        assertEquals(DriveDocumentInventoryStatus.OK, result.single { it.driveFileId == fixtures[1].driveFileId }.status)
        assertEquals(DriveDocumentInventoryStatus.MULTIPLE_REFERENCES, result.single { it.driveFileId == "shared-drive-25" }.status)
        assertEquals(DriveDocumentInventoryStatus.MIGRATION_PRUEFEN, result.single { it.driveFileId == fixtures[27].driveFileId }.status)
        assertEquals(DriveDocumentInventoryStatus.LEGACY_LAYOUT, result.single { it.driveFileId == fixtures[30].driveFileId }.status)
        assertEquals(DriveDocumentInventoryStatus.MISSING_LOCAL_REFERENCE, result.single { it.driveFileId == fixtures[22].driveFileId }.status)
        assertEquals(DriveDocumentInventoryStatus.MISSING_INDEX_REFERENCE, result.single { it.driveFileId == fixtures[2].driveFileId }.status)
        assertTrue(result.any { it.status == DriveDocumentInventoryStatus.ORPHAN })
        assertTrue(result.count { it.status == DriveDocumentInventoryStatus.POSSIBLE_DUPLICATE } >= 2)
    }

    @Test fun `duplicates and migration never auto merge delete or alter ambiguous originals`() {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val original = ManagedDocument("doc-original", "property-test", originalFilename = fixtures[0].filename, sha256 = fixtures[0].sha256, fileSizeBytes = fixtures[0].bytes.size.toLong())
        val exactCopy = ManagedDocument("doc-copy", "property-test", originalFilename = fixtures[18].filename, sha256 = fixtures[18].sha256, fileSizeBytes = fixtures[18].bytes.size.toLong())
        val sameNameDifferentContent = ManagedDocument("doc-other", "property-test", originalFilename = fixtures[19].filename, sha256 = fixtures[19].sha256, fileSizeBytes = fixtures[19].bytes.size.toLong())
        assertEquals(DocumentDuplicateKind.EXACT, DocumentDuplicatePolicy.detect(exactCopy, listOf(original)).kind)
        assertEquals(DocumentDuplicateKind.NONE, DocumentDuplicatePolicy.detect(sameNameDifferentContent, listOf(original)).kind)

        val ambiguous = DocumentStorageMigrationPlanner.preview(listOf(
            DocumentMigrationCandidate("receipt-a", "drive-a", "legacy", "a.pdf", "new", "same.pdf", fixtures[0].sha256),
            DocumentMigrationCandidate("receipt-b", "drive-b", "legacy", "b.pdf", "new", "same.pdf", fixtures[0].sha256)
        ))
        assertTrue(ambiguous.items.all { it.action == DocumentMigrationAction.PRUEFEN })
        assertEquals(2, ambiguous.items.size)
    }

    @Test fun `one confirmed legacy migration completes journal with stable Drive id and content hash`() {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 31 }
        val fakeDrive = InMemoryDocumentDrive()
        val original = fakeDrive.upload(fixture, "legacy-folder")
        val item = DocumentStorageMigrationPlanner.preview(listOf(
            DocumentMigrationCandidate(
                receiptInternalId = fixture.receiptInternalId,
                driveFileId = original.id,
                currentFolderId = original.parentId,
                currentFilename = original.name,
                targetFolderId = "property:property-test/02_belege/2025",
                targetFilename = "2025-02-14_TestHandwerk_1249-90EUR_BLG-2025-00001.pdf",
                beforeSha256 = original.sha256
            )
        )).items.single()
        assertEquals(DocumentMigrationAction.VERSCHIEBEN_UND_UMBENENNEN, item.action)
        val planned = DocumentStorageMigrationPlanner.journal(item)
        assertEquals("PLANNED", planned.state)

        val applied = fakeDrive.reorganize(
            original.id, item.candidate.targetFolderId, item.candidate.targetFilename, item.candidate.beforeSha256
        )
        val completed = planned.copy(state = if (applied.canApply) "COMPLETED" else "FAILED")

        assertEquals("COMPLETED", completed.state)
        assertEquals(original.id, applied.file.id)
        assertEquals(original.sha256, applied.file.sha256)
        assertTrue(DocumentMigrationVerifier.isContentPreserved(planned.beforeSha256, applied.file.sha256))
    }

    @Test fun `wrong expected hash blocks fake Drive reorganization without modifying the file`() {
        val fixture = SyntheticDocumentFixtureFactory.create().single { it.number == 29 }
        val fakeDrive = InMemoryDocumentDrive()
        val original = fakeDrive.upload(fixture, "legacy-folder")

        val blocked = fakeDrive.reorganize(original.id, "new-folder", "new-name.pdf", fixture.declaredSha256!!)

        assertFalse(blocked.canApply)
        assertEquals("DRIVE_REORGANIZATION_PENDING", blocked.status)
        assertEquals(original, fakeDrive.file(original.id))
    }

    private fun targetRoute(fixture: SyntheticDocumentFixture): DocumentRoute = DocumentDrivePathResolver.route(
        propertyId = "property-test", propertyName = "Testobjekt", propertyAddress = "Musterstrasse 12",
        type = fixture.expectedType, documentDate = documentDate(fixture),
        unitId = if (fixture.expectedType == ManagedDocumentType.MIETVERTRAG) "unit-test-01" else null,
        unitLabel = if (fixture.expectedType == ManagedDocumentType.MIETVERTRAG) "WE_01" else null
    )

    private fun documentDate(fixture: SyntheticDocumentFixture) = if (fixture.number == 9) "2025-10-01" else "2026-01-01"
}

private class FixtureAiProvider {
    fun analyze(fixture: SyntheticDocumentFixture): ManagedDocumentAiResult {
        val fields = when (fixture.expectedType) {
            ManagedDocumentType.KAUFVERTRAG -> listOf(
                ManagedDocumentAiField("kaufpreis", "Kaufpreis", "520000", 0.96, "Seite 1"),
                ManagedDocumentAiField("adresse", "Objektadresse", "Musterstrasse 12", 0.88, "Seite 1")
            )
            else -> emptyList()
        }
        val unknown = fixture.expectedType == ManagedDocumentType.SONSTIGES
        return ManagedDocumentAiResult(
            documentType = fixture.expectedType.name,
            confidence = if (unknown) 0.2 else 0.94,
            suggestedPropertyId = if (unknown) "" else "property-test",
            suggestedUnitId = if (fixture.expectedType == ManagedDocumentType.MIETVERTRAG) "unit-test-01" else "",
            fields = fields
        )
    }
}

private data class FakeDriveFile(val id: String, val parentId: String, val name: String, val bytes: ByteArray, val sha256: String) {
    override fun equals(other: Any?): Boolean = other is FakeDriveFile && id == other.id && parentId == other.parentId && name == other.name && bytes.contentEquals(other.bytes) && sha256 == other.sha256
    override fun hashCode(): Int = 31 * id.hashCode() + sha256.hashCode()
}

private data class FakeDriveResult(val canApply: Boolean, val status: String, val file: FakeDriveFile)

private class InMemoryDocumentDrive {
    var available = true
    private val files = linkedMapOf<String, FakeDriveFile>()

    fun upload(fixture: SyntheticDocumentFixture, parentId: String): FakeDriveFile = FakeDriveFile(
        fixture.driveFileId, parentId, fixture.filename, fixture.bytes.copyOf(), fixture.sha256
    ).also { files[it.id] = it }

    fun file(id: String): FakeDriveFile = requireNotNull(files[id])
    fun snapshot(): List<FakeDriveFile> = files.values.map { it.copy(bytes = it.bytes.copyOf()) }

    fun reorganize(id: String, targetFolder: String, targetName: String, expectedHash: String): FakeDriveResult {
        val current = file(id)
        if (!available) return FakeDriveResult(false, "DRIVE_REORGANIZATION_PENDING", current)
        val plan = ManagedDocumentDriveReorganization.plan(
            reachable = true,
            currentParentIds = listOf(current.parentId),
            currentFilename = current.name,
            targetFolderId = targetFolder,
            targetFilename = targetName,
            expectedSha256 = expectedHash,
            actualSha256 = StableDocumentIdentity.sha256(current.bytes)
        )
        if (!plan.canApply) return FakeDriveResult(false, "DRIVE_REORGANIZATION_PENDING", current)
        val updated = current.copy(parentId = targetFolder, name = targetName)
        assertArrayEquals(current.bytes, updated.bytes)
        files[id] = updated
        return FakeDriveResult(true, "SYNCED", updated)
    }
}
