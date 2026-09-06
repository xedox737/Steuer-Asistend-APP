package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.util.LogbookCsvExporter
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SupplementalDriveBackupTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = database.close()

    @Test fun tripAndStandardRouteRestoreCompletelyAndIdempotently() = runTest {
        val now = "2026-09-02T10:00:00Z"
        val route = TripRouteNormalizer.normalize("A", listOf(TripStop("B")), "C", TripRouteMode.INDIVIDUELL, false)
        database.logbookDao().upsertTrip(
            LogbookTrip(
                id = 41, date = "2026-09-02", time = "10:00", purpose = "Materialkauf",
                propertyReference = "Objekt", startAddress = "A", destinationAddress = "C",
                stopsJson = TripStopJson.encode(route.stops), routeMode = TripRouteMode.INDIVIDUELL.name,
                taxDistanceKm = 27.5, kilometerSource = KilometerSource.MANUELL.name,
                aiEstimatedKm = 31.0, routedKm = 24.7, manualKm = 27.5,
                odometerStartKm = 100.0, odometerEndKm = 127.5,
                plausibilityStatus = TripPlausibilityStatus.MANUELL_BESTAETIGT.name,
                manuallyConfirmed = true, sourceReceiptId = 7, expenseReceiptId = 8,
                routeProvider = "GOOGLE_ROUTES", routeCalculatedAt = now, routeDurationSeconds = 1800,
                correctionReason = "Umleitung", correctionNote = "Baustelle; \"Süd\"", routeSignature = route.signature,
                note = "Beleg vorhanden", createdAt = now, updatedAt = now
            )
        )
        database.logbookDao().upsertStandardRoute(
            StandardRoute(
                id = 17, name = "A-B-C", startAddress = "A", destinationAddress = "C",
                stopsJson = TripStopJson.encode(route.stops), routeMode = TripRouteMode.INDIVIDUELL.name,
                distanceKm = 24.7, routeSignature = route.signature, sourceProvider = "GOOGLE_ROUTES",
                createdAt = now, updatedAt = now
            )
        )
        context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE).edit()
            .putString("google_routes_key_ciphertext", "MUST_NOT_LEAVE_DEVICE").apply()

        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertEquals(4, payload.getInt("schemaVersion"))
        assertFalse(payload.toString().contains("MUST_NOT_LEAVE_DEVICE"))
        database.logbookDao().deleteTrip(41)
        database.logbookDao().deleteStandardRoute(17)

        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restored = database.logbookDao().getAllTrips()
        val restoredRoutes = database.logbookDao().getAllStandardRoutes()
        assertEquals(1, restored.size)
        assertEquals(1, restoredRoutes.size)
        assertEquals("Umleitung", restored.single().correctionReason)
        assertEquals("GOOGLE_ROUTES", restored.single().routeProvider)
        assertEquals(route.signature, restoredRoutes.single().routeSignature)

        val csv = LogbookCsvExporter.create(restored)
        assertTrue(csv.contains("Route Provider"))
        assertTrue(csv.contains("GOOGLE_ROUTES"))
        assertTrue(csv.contains("Umleitung"))
        assertTrue(csv.contains("A -> B -> C"))
        assertTrue(csv.contains("\"Baustelle; \"\"Süd\"\"\""))
        assertFalse(csv.contains("MUST_NOT_LEAVE_DEVICE"))
    }

    @Test fun propertyPortfolioAndLoanAssignmentsRestoreIdempotently() = runTest {
        val second = PropertyMetadata(id = 2, propertyId = "property-2", name = "Zweites Objekt", wohneinheiten = "WE 01")
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 1, propertyId = "property-1", name = "Bestand"))
        database.propertyDao().insertPropertyMetadata(second)
        database.loanDao().upsertLoan(Loan(id = 22, bezeichnung = "Objektdarlehen", propertyId = second.propertyId))
        context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE).edit()
            .putString("property_property-2_unit_WE 01_label", "OG links")
            .apply()

        val payload = SupplementalDriveBackup.createPayload(context, database)
        database.propertyDao().insertPropertyMetadata(second.copy(name = "Zwischenstand"))
        database.loanDao().deleteLoan(22)
        context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE).edit().clear().apply()

        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)

        assertEquals("Zweites Objekt", database.propertyDao().getPropertyByPropertyId("property-2")?.name)
        assertEquals("property-2", database.loanDao().getAllLoans().single { it.id == 22 }.propertyId)
        assertEquals("OG links", context.getSharedPreferences("wohneinheiten_prefs", Context.MODE_PRIVATE)
            .getString("property_property-2_unit_WE 01_label", null))
    }

    @Test fun managedDocumentsRestoreCompletelyWithoutOcrPayloadAndRemainIdempotent() = runTest {
        val document = ManagedDocument(
            documentId = "doc-1", propertyId = "property-1", unitId = "unit-1",
            documentType = ManagedDocumentType.MIETVERTRAG.name, documentDate = "2026-10-01",
            title = "Mietvertrag Mustermann", originalFilename = "scan.pdf", storedFilename = "Mietvertrag.pdf",
            mimeType = "application/pdf", localUri = "/private/device/path.pdf", driveFileId = "drive-1",
            driveFolderId = "folder-1", sha256 = "abc", fileSizeBytes = 123,
            createdAt = "now", updatedAt = "now", ocrStatus = DocumentProcessingStatus.ERFOLGREICH.name,
            ocrText = "sensibler Volltext", extractedFieldsJson = "{\"mieter\":\"Mustermann\"}"
        )
        database.managedDocumentDao().upsert(document)
        val payload = SupplementalDriveBackup.createPayload(context, database)
        assertFalse(payload.toString().contains("sensibler Volltext"))
        assertFalse(payload.toString().contains("/private/device/path.pdf"))
        database.managedDocumentDao().deleteById(document.documentId)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        SupplementalDriveBackup.restorePayload(context, database, payload)
        val restored = database.managedDocumentDao().getAll().single()
        assertEquals("drive-1", restored.driveFileId)
        assertEquals("abc", restored.sha256)
        assertEquals("", restored.ocrText)
        assertEquals(DocumentProcessingStatus.AUSSTEHEND.name, restored.ocrStatus)
    }

    @Test fun providerSecretsAccessTokensOcrTextAndPrivatePathsNeverEnterBackup() = runTest {
        context.getSharedPreferences("ai_provider_settings", Context.MODE_PRIVATE).edit()
            .putString("openai_api_key_ciphertext", "OPENAI_SECRET_TEST_SENTINEL")
            .putString("gemini_api_key_ciphertext", "GEMINI_SECRET_TEST_SENTINEL")
            .putString("google_routes_key_ciphertext", "ROUTES_SECRET_TEST_SENTINEL")
            .putString("access_token", "ACCESS_TOKEN_TEST_SENTINEL")
            .apply()
        database.managedDocumentDao().upsert(
            ManagedDocument(
                documentId = "security-document", propertyId = "property-test",
                localUri = "/private/device/security-document.pdf",
                ocrText = "LOCAL_OCR_TEXT_TEST_SENTINEL"
            )
        )

        val serialized = SupplementalDriveBackup.createPayload(context, database).toString()

        listOf(
            "OPENAI_SECRET_TEST_SENTINEL", "GEMINI_SECRET_TEST_SENTINEL",
            "ROUTES_SECRET_TEST_SENTINEL", "ACCESS_TOKEN_TEST_SENTINEL",
            "/private/device/security-document.pdf", "LOCAL_OCR_TEXT_TEST_SENTINEL"
        ).forEach { forbidden -> assertFalse("Backup enthält $forbidden", serialized.contains(forbidden)) }
        assertTrue(JSONObject(serialized).getJSONArray("managedDocuments").length() == 1)
    }

    @Test fun fullTextSearchFindsVendorReceiptIdAmountOcrAndHonorsFilters() = runTest {
        val repository = ReceiptRepository(
            database.receiptDao(), database.propertyDao(), database.receiptEntityDao(), database.belegDao(),
            database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao()
        )
        val receipt = Receipt(
            aussteller = "Hornbach", datum = "2026-09-04", uhrzeit = "", bruttobetrag = 84.5,
            hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800", beschreibung = "Farbe",
            internalId = "receipt-1", displayId = "BLG-2026-00127"
        )
        val document = ManagedDocument(
            documentId = "receipt:receipt-1", propertyId = "property-1", unitId = "unit-1",
            receiptInternalId = "receipt-1", documentType = ManagedDocumentType.RECHNUNG.name,
            documentCategory = "02_Belege/2026", documentDate = "2026-09-04", title = "Rechnung",
            ocrText = "Rechnungsnummer AB-4711"
        )
        repository.upsertManagedDocument(document, receipt)
        assertEquals(1, repository.searchManagedDocuments("Hornbach").size)
        assertEquals(1, repository.searchManagedDocuments("AB-4711").size)
        assertEquals(1, repository.searchManagedDocuments("BLG-2026-00127").size)
        assertEquals(1, repository.searchManagedDocuments("84.50").size)
        assertEquals(1, repository.searchManagedDocuments("", "property-1", "unit-1", "2026", ManagedDocumentType.RECHNUNG.name, "02_Belege/2026").size)
        assertTrue(repository.searchManagedDocuments("", "other-property").isEmpty())
    }

    @Test fun schemaOneWithoutLogbookArraysStillRestores() = runTest {
        SupplementalDriveBackup.restorePayload(context, database, JSONObject("""{"schemaVersion":1,"loans":[]}"""))
        assertTrue(database.logbookDao().getAllTrips().isEmpty())
        assertTrue(database.logbookDao().getAllStandardRoutes().isEmpty())
    }

    @Test fun coreRestoreClearingLeavesSupplementalLoansLogbookAndDocumentsUntouched() = runTest {
        database.loanDao().upsertLoan(Loan(id = 91, bezeichnung = "Bestand"))
        database.logbookDao().upsertTrip(LogbookTrip(
            id = 92, date = "2026-09-05", purpose = "Bestand", startAddress = "A", destinationAddress = "B",
            taxDistanceKm = 1.0, kilometerSource = KilometerSource.MANUELL.name, createdAt = "now", updatedAt = "now"
        ))
        database.managedDocumentDao().upsert(ManagedDocument("doc-preserved", "property-1"))
        val repository = ReceiptRepository(
            database.receiptDao(), database.propertyDao(), database.receiptEntityDao(), database.belegDao(),
            database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao()
        )
        repository.clearCoreRestoreRelevantTables()
        assertEquals("Bestand", database.loanDao().getAllLoans().single().bezeichnung)
        assertEquals("Bestand", database.logbookDao().getAllTrips().single().purpose)
        assertEquals("doc-preserved", database.managedDocumentDao().getAll().single().documentId)
    }

    @Test fun oldBackupWithoutManagedDocumentsDoesNotClearExistingDocuments() = runTest {
        database.managedDocumentDao().upsert(ManagedDocument("doc-existing", "property-1"))
        SupplementalDriveBackup.restorePayload(
            context, database, JSONObject("""{"schemaVersion":2,"loans":[]}"""), replaceManagedDocuments = true
        )
        assertEquals("doc-existing", database.managedDocumentDao().getAll().single().documentId)
    }

    @Test fun corruptSupplementalDocumentPayloadRollsBackInsteadOfDestroyingExistingRows() = runTest {
        database.managedDocumentDao().upsert(ManagedDocument("doc-existing", "property-1"))
        val corrupt = JSONObject().apply {
            put("schemaVersion", 3)
            put("managedDocuments", org.json.JSONArray().put("not-an-object"))
        }

        val failure = runCatching {
            SupplementalDriveBackup.restorePayload(context, database, corrupt, replaceManagedDocuments = true)
        }

        assertTrue(failure.isFailure)
        assertEquals(listOf("doc-existing"), database.managedDocumentDao().getAll().map { it.documentId })
    }
}
