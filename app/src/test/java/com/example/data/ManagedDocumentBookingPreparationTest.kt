package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.util.DatevReceiptEligibility
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManagedDocumentBookingPreparationTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: ReceiptRepository
    private lateinit var service: ManagedDocumentBookingPreparation
    private lateinit var original: File
    private lateinit var source: ManagedDocument
    private val bytes = "%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".toByteArray()

    @Before fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repository = ReceiptRepository(database.receiptDao(), database.propertyDao(), database.receiptEntityDao(),
            database.belegDao(), database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao())
        service = ManagedDocumentBookingPreparation(context, database, repository)
        context.getSharedPreferences("google_drive_prefs", 0).edit().putString("selected_property_id", "booking-property-a").commit()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "booking-property-a", name = "Anderes Haus",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("booking-unit-a", "WE 01")))))
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 2, propertyId = "booking-property-b", name = "Dokumenthaus",
            wohneinheiten = UnitIdentity.encode(listOf(UnitIdentity.Entry("booking-unit-b", "WE 01")))))
        original = File(context.filesDir, "booking-preparation-${UUID.randomUUID()}.pdf").apply { writeBytes(bytes) }
        source = ManagedDocument(documentId = "booking-document", propertyId = "booking-property-b", unitId = "booking-unit-b",
            documentType = ManagedDocumentType.RECHNUNG.name, documentDate = "2026-11-01", title = "Handwerkerrechnung",
            originalFilename = "Handwerkerrechnung.pdf", storedFilename = original.name, mimeType = "application/pdf",
            localUri = original.absolutePath, sha256 = StableDocumentIdentity.sha256(bytes), fileSizeBytes = bytes.size.toLong(),
            reviewStatus = DocumentReviewStatus.GEPRUEFT.name, ocrStatus = DocumentProcessingStatus.ERFOLGREICH.name,
            ocrText = "Rechnungsbetrag 9999 Euro, Sachkonto 4800", extractedFieldsJson = "{\"betrag\":9999,\"konto\":\"4800\"}",
            createdAt = "2026-11-01T12:00:00Z", updatedAt = "2026-11-01T12:00:00Z")
        repository.upsertManagedDocument(source)
    }

    @After fun tearDown() {
        if (::database.isInitialized) {
            runBlocking {
                database.receiptDao().getAllReceiptsIncludingDeletedList().forEach { receipt ->
                    ReceiptOriginalChain.paths(receipt.imageUrl).forEach { File(it.removePrefix("file://")).delete() }
                }
            }
            database.close()
        }
        if (::original.isInitialized) original.delete()
        if (::context.isInitialized) context.getSharedPreferences("google_drive_prefs", 0).edit().remove("selected_property_id").commit()
    }

    @Test fun checkedInvoiceBecomesUnapprovedDraftWithOriginalScopeAndBytes() = runBlocking {
        val receipt = service.prepare(source.documentId)
        assertEquals("booking-property-b", receipt.propertyId)
        assertEquals("booking-unit-b", receipt.unitId)
        assertTrue(receipt.internalId.isNotBlank())
        assertEquals(0.0, receipt.bruttobetrag, 0.0)
        assertEquals("", receipt.kontoNr)
        assertEquals("", receipt.hauptkategorie)
        assertEquals("", receipt.unterkategorie)
        assertEquals("OFFEN", receipt.freigabestatus)
        assertEquals("ZU_PRUEFEN", receipt.pruefstatus)
        assertFalse(DatevReceiptEligibility.isAccountingApproved(receipt))
        assertTrue(DatevReceiptEligibility.issues(receipt).any { it.code == "NOT_APPROVED" })
        assertTrue(AccountingApprovalJson.decodeAllocations(receipt.allocationsJson).isEmpty())
        assertTrue(AccountingApprovalJson.decodeBookingProposals(receipt.bookingProposalsJson).isEmpty())
        assertEquals(source, database.managedDocumentDao().getById(source.documentId))
        assertArrayEquals(bytes, original.readBytes())
        val receiptOriginal = File(ReceiptOriginalChain.paths(receipt.imageUrl).first().removePrefix("file://"))
        assertArrayEquals(bytes, receiptOriginal.readBytes())
        assertNotEquals(original.canonicalPath, receiptOriginal.canonicalPath)
        val indexed = database.managedDocumentDao().getByReceiptId(receipt.internalId)!!
        assertEquals(source.sha256, indexed.sha256)
        assertEquals(source.propertyId, indexed.propertyId)
        assertEquals(source.unitId, indexed.unitId)
    }

    @Test fun repeatedPreparationReturnsSameDraftWithoutDuplicateReceiptsOrOriginalIndex() = runBlocking {
        val first = service.prepare(source.documentId)
        val second = service.prepare(source.documentId)
        assertEquals(first.id, second.id)
        assertEquals(first.internalId, second.internalId)
        assertEquals(1, database.receiptDao().getAllReceiptsList().size)
        assertEquals(1, database.receiptEntityDao().getAllEntities().first().size)
        assertEquals(1, database.managedDocumentDao().getAllByReceiptId(first.internalId).size)
        assertEquals(source, database.managedDocumentDao().getById(source.documentId))
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun sameOriginalInAnotherPropertyCannotSilentlyReuseOrDuplicateAnExistingDraft() = runBlocking {
        val first = service.prepare(source.documentId)
        val differentScope = source.copy(documentId = "other-property-copy", propertyId = "booking-property-a", unitId = "booking-unit-a")
        repository.upsertManagedDocument(differentScope)
        assertTrue(runCatching { service.prepare(differentScope.documentId) }.isFailure)
        assertEquals(listOf(first), database.receiptDao().getAllReceiptsList())
        assertEquals(source, database.managedDocumentDao().getById(source.documentId))
        assertEquals(differentScope, database.managedDocumentDao().getById(differentScope.documentId))
        assertArrayEquals(bytes, original.readBytes())
    }

    @Test fun missingOriginalRejectsBeforeAnyReceiptOrIndexIsWritten() = runBlocking {
        assertTrue(original.delete())
        assertTrue(runCatching { service.prepare(source.documentId) }.isFailure)
        assertNoDraftOrIndex()
    }

    @Test fun changedOriginalRejectsBeforeAnyReceiptOrIndexIsWritten() = runBlocking {
        original.writeBytes(bytes + "tampered".toByteArray())
        assertTrue(runCatching { service.prepare(source.documentId) }.isFailure)
        assertNoDraftOrIndex()
    }

    @Test fun foreignUnitRejectsBeforeAnyReceiptOrIndexIsWritten() = runBlocking {
        val invalid = source.copy(unitId = "booking-unit-a")
        repository.upsertManagedDocument(invalid)
        assertTrue(runCatching { service.prepare(source.documentId) }.isFailure)
        assertNoDraftOrIndex(expectedSource = invalid)
    }

    @Test fun removedPropertyRejectsBeforeAnyReceiptOrIndexIsWritten() = runBlocking {
        database.propertyDao().deletePropertyByPropertyId(source.propertyId)
        assertTrue(runCatching { service.prepare(source.documentId) }.isFailure)
        assertNoDraftOrIndex()
    }

    private suspend fun assertNoDraftOrIndex(expectedSource: ManagedDocument = source) {
        assertTrue(database.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertTrue(database.receiptEntityDao().getAllEntities().first().isEmpty())
        assertEquals(listOf(expectedSource), database.managedDocumentDao().getAll())
    }
}
