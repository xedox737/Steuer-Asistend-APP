package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyntheticDocumentSearchTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: ReceiptRepository

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = ReceiptRepository(
            database.receiptDao(), database.propertyDao(), database.receiptEntityDao(), database.belegDao(),
            database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao()
        )
    }

    @After fun tearDown() = database.close()

    @Test fun `synthetic OCR metadata and receipt fields are searchable with all filters`() = runTest {
        val fixtures = SyntheticDocumentFixtureFactory.create()
        val searchable = listOf(
            document(fixtures, 1, "Heizungsventil Rechnungsnummer TH-2026-001", ManagedDocumentType.RECHNUNG, "unit-02"),
            document(fixtures, 2, "TestBaumarkt Material 84,50 EUR", ManagedDocumentType.RECHNUNG),
            document(fixtures, 4, "Grundsteuer Gemeinde Musterstadt", ManagedDocumentType.GRUNDSTEUERDOKUMENT),
            document(fixtures, 9, "Kaufvertrag Kaufpreis 520000 EUR", ManagedDocumentType.KAUFVERTRAG),
            document(fixtures, 10, "Testbank Darlehen 450000 EUR", ManagedDocumentType.DARLEHENSVERTRAG),
            document(fixtures, 12, "Energieausweis Baujahr 1998", ManagedDocumentType.ENERGIEAUSWEIS),
            document(fixtures, 13, "Mietvertrag WE_01 Kaltmiete", ManagedDocumentType.MIETVERTRAG, "unit-01")
        )
        searchable.forEach { repository.upsertManagedDocument(it) }

        listOf("Heizungsventil", "Testbank", "520000", "WE_01", "Energieausweis", "Grundsteuer", "84,50", "Rechnungsnummer")
            .forEach { query -> assertTrue("Keine Treffer für $query", repository.searchManagedDocuments(query).isNotEmpty()) }
        assertEquals("synthetic-09", repository.searchManagedDocuments("520000").single().documentId)
        assertEquals(1, repository.searchManagedDocuments("", propertyId = "property-test", year = "2026", documentType = ManagedDocumentType.MIETVERTRAG.name, unitId = "unit-01").size)
        assertTrue(repository.searchManagedDocuments("", propertyId = "other-property").isEmpty())
        assertEquals(searchable.size, repository.searchManagedDocuments("").size)
    }

    private fun document(
        fixtures: List<SyntheticDocumentFixture>,
        number: Int,
        text: String,
        type: ManagedDocumentType,
        unitId: String? = null
    ): ManagedDocument {
        val fixture = fixtures.single { it.number == number }
        return ManagedDocument(
            documentId = "synthetic-${number.toString().padStart(2, '0')}",
            propertyId = "property-test",
            unitId = unitId,
            documentType = type.name,
            documentCategory = fixture.expectedTargetSuffix,
            documentDate = if (number == 9) "2025-10-01" else "2026-02-14",
            title = fixture.filename.substringBeforeLast('.'),
            originalFilename = fixture.filename,
            storedFilename = fixture.filename,
            sha256 = fixture.sha256,
            fileSizeBytes = fixture.bytes.size.toLong(),
            ocrStatus = DocumentProcessingStatus.ERFOLGREICH.name,
            ocrText = text
        )
    }
}
