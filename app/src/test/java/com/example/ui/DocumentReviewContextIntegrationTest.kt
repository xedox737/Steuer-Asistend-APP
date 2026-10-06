package com.example.ui

import android.app.Application
import com.example.data.AppDatabase
import com.example.data.DocumentFieldDecision
import com.example.data.DocumentFieldProposal
import com.example.data.ManagedDocument
import com.example.data.ManagedDocumentType
import com.example.data.PropertyMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DocumentReviewContextIntegrationTest {
    @get:Rule val applicationIsolation = IsolatedAndroidApplicationRule()

    private lateinit var application: Application
    private lateinit var database: AppDatabase
    private lateinit var viewModel: ReceiptViewModel

    @Before
    fun setUp() {
        application = androidx.test.core.app.ApplicationProvider.getApplicationContext()
        database = AppDatabase.getDatabase(application, CoroutineScope(Dispatchers.IO))
        runBlocking(Dispatchers.IO) {
            database.clearAllTables()
            database.propertyDao().insertPropertyMetadata(
                PropertyMetadata(id = 301, propertyId = "property-a", name = "Objekt A", adresse = "Adresse A")
            )
            database.propertyDao().insertPropertyMetadata(
                PropertyMetadata(id = 302, propertyId = "property-b", name = "Objekt B", adresse = "Adresse B")
            )
            database.managedDocumentDao().upsert(
                ManagedDocument(
                    documentId = "document-b",
                    propertyId = "property-b",
                    title = "Dokument B",
                    documentType = ManagedDocumentType.SONSTIGES.name
                )
            )
        }
        viewModel = ReceiptViewModel(application)
        viewModel.selectProperty("property-a")
        runBlocking {
            withTimeout(5_000) {
                while (viewModel.propertyMetadata.value?.propertyId != "property-a") delay(20)
            }
        }
    }

    @After
    fun tearDown() {
        runBlocking(Dispatchers.IO) { database.clearAllTables() }
    }

    @Test
    fun confirmReviewUsesDocumentPropertyInsteadOfGloballySelectedProperty() = runBlocking {
        viewModel.confirmManagedDocumentReview(
            documentId = "document-b",
            type = ManagedDocumentType.SONSTIGES,
            date = "2026-10-06",
            unitId = null,
            proposals = listOf(
                DocumentFieldProposal(
                    key = "objektadresse",
                    label = "Objektadresse",
                    detectedValue = "Neue Adresse B",
                    decision = DocumentFieldDecision.UEBERNEHMEN
                )
            )
        )

        withTimeout(5_000) {
            while (database.propertyDao().getPropertyByPropertyId("property-b")?.adresse != "Neue Adresse B") delay(20)
        }

        assertEquals("Adresse A", database.propertyDao().getPropertyByPropertyId("property-a")?.adresse)
        assertEquals("Neue Adresse B", database.propertyDao().getPropertyByPropertyId("property-b")?.adresse)
        assertEquals("property-a", viewModel.propertyMetadata.value?.propertyId)
    }

    @Test
    fun missingUnitBlocksReviewWithoutWritingPropertyValues() = runBlocking {
        viewModel.confirmManagedDocumentReview(
            documentId = "document-b",
            type = ManagedDocumentType.SONSTIGES,
            date = "2026-10-06",
            unitId = "missing-unit",
            proposals = listOf(
                DocumentFieldProposal(
                    key = "objektadresse",
                    label = "Objektadresse",
                    detectedValue = "Darf nicht gespeichert werden",
                    decision = DocumentFieldDecision.UEBERNEHMEN
                )
            )
        )

        withTimeout(5_000) {
            while (!viewModel.documentOperationStatus.value.orEmpty().contains("Wohneinheit")) delay(20)
        }

        assertEquals("Adresse B", database.propertyDao().getPropertyByPropertyId("property-b")?.adresse)
    }
}
