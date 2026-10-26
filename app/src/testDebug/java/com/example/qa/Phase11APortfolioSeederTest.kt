package com.example.qa

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.IsolatedAppDatabaseRule
import com.example.util.DatevProfileService
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class Phase11APortfolioSeederTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private val runId = "P11A-2026-10-21-UNIT-UI-SEED"

    @After fun cleanup() {
        // Only this isolated Robolectric sandbox, never a real app installation.
        context.getSharedPreferences("phase11a_seed_journal", 0).edit().clear().commit()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        File(context.filesDir, "managed_documents").deleteRecursively()
    }

    @Test fun bundledPortfolioHasAllStableIdsCountsRelationsAndPhysicalOriginals() = runBlocking {
        Phase11APortfolioSeeder.seedBundled(context, db, runId)
        val payload = SupplementalDriveBackup.createPayload(context, db)
        assertEquals(5, db.propertyDao().getAllProperties().size)
        assertEquals(120, db.bankDao().getAllTransactions().size)
        assertEquals(10, db.logbookDao().getAllTrips().size)
        assertEquals(1, db.loanDao().getAllLoans().size)
        val rows = db.receiptDao().getAllReceiptsIncludingDeletedList()
        assertEquals(70, rows.size)
        val fixture = File(context.cacheDir, "test-fixture.zip")
        context.assets.open(Phase11APortfolioSeeder.ASSET).use { input -> fixture.outputStream().use { input.copyTo(it) } }
        ZipFile(fixture).use { zip ->
            val expectedIds = zip.entries().asSequence().filter { it.name.startsWith("metadata/") }
                .map { it.name.removePrefix("metadata/").removeSuffix(".json") }.toSet()
            assertEquals(expectedIds, rows.map { it.internalId }.toSet())
            val docs = db.managedDocumentDao().getAll()
            assertEquals(70, docs.size)
            val propertyIds = db.propertyDao().getAllProperties().map { it.propertyId }.toSet()
            assertTrue(rows.all { it.propertyId in propertyIds })
            assertTrue(docs.all { it.receiptInternalId in expectedIds && it.propertyId in propertyIds })
            val source = JSONObject(zip.getInputStream(zip.getEntry("supplemental.json")).bufferedReader().use { it.readText() })
            val sourceDocs = source.getJSONArray("managedDocuments")
            val expectedDocs = (0 until sourceDocs.length()).map { sourceDocs.getJSONObject(it) }.associateBy { it.getString("documentId") }
            assertEquals(expectedDocs.keys, docs.map { it.documentId }.toSet())
            docs.filter { it.fileSizeBytes > 0 }.also { assertEquals(60, it.size) }.forEach { doc ->
                val original = expectedDocs.getValue(doc.documentId)
                val file = File(doc.localUri)
                assertEquals(original.getString("sha256"), StableDocumentIdentity.sha256(file.readBytes()))
                assertEquals(original.getLong("fileSizeBytes"), file.length())
                assertEquals(original.getString("mimeType"), doc.mimeType)
                assertEquals(original.getString("receiptInternalId"), doc.receiptInternalId)
                assertTrue(rows.single { it.internalId == doc.receiptInternalId }.imageUrl.contains(file.absolutePath))
            }
        }
        val repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(), db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        val drive = DrivePersistenceRepository(context, repository)
        assertEquals(17, db.propertyDao().getAllProperties().sumOf { drive.getWohneinheitenFromPrefs(it).size })
        val snapshot = Phase11ASnapshot.capture(context, db, JSONObject().put("runId", runId).put("label", "ui-unit-seed")
            .put("source", "UNIT_TEST").put("commit", System.getenv("GITHUB_SHA") ?: "f68ce4c45616601c115573423eb7783cd5c25eae")
            .put("capturedAt", java.time.Instant.now().toString()).put("device", "Robolectric")
            .put("android", android.os.Build.VERSION.RELEASE).put("appVersion", "1.0"))
        File("build/reports/phase11a-ui/unit-seed.json").apply { requireNotNull(parentFile).mkdirs(); writeText(snapshot.toString(2)) }
        assertEquals(70, payload.getJSONArray("managedDocuments").length())
    }

    @Test fun explicitInstrumentationFileEntryUsesTheSameBundledStableIds() = runBlocking {
        val file = File(context.cacheDir, "instrumentation-input.zip")
        context.assets.open(Phase11APortfolioSeeder.ASSET).use { input -> file.outputStream().use { input.copyTo(it) } }
        val hash = context.assets.open("phase11a/qa-transport-snapshot.sha256").bufferedReader().use { it.readText().trim() }
        Phase11APortfolioSeeder.seed(context, db, file, hash, runId)
        ZipFile(file).use { zip ->
            val ids = zip.entries().asSequence().filter { it.name.startsWith("metadata/") }
                .map { it.name.removePrefix("metadata/").removeSuffix(".json") }.toSet()
            assertEquals(ids, db.receiptDao().getAllReceiptsIncludingDeletedList().map { it.internalId }.toSet())
        }
        assertEquals(60, db.managedDocumentDao().getAll().count { it.fileSizeBytes > 0 && File(it.localUri).isFile })
    }

    @Test fun secondLoadIsRefusedWithoutChangingAnyIdsHashesOrCounts() = runBlocking {
        Phase11APortfolioSeeder.seedBundled(context, db, runId)
        val before = db.receiptDao().getAllReceiptsIncludingDeletedList()
        val files = db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }.associate { it.documentId to File(it.localUri).readBytes().toList() }
        assertNotNull(runCatching { Phase11APortfolioSeeder.seedBundled(context, db, runId) }.exceptionOrNull())
        assertEquals(before, db.receiptDao().getAllReceiptsIncludingDeletedList())
        assertEquals(files, db.managedDocumentDao().getAll().filter { it.fileSizeBytes > 0 }.associate { it.documentId to File(it.localUri).readBytes().toList() })
    }

    @Test fun productionPackageRefusedBeforeDomainOrJournalWrites() = runBlocking {
        val production = object : ContextWrapper(context) { override fun getPackageName() = Phase11AIsolation.PRODUCTION }
        val error = runCatching { Phase11APortfolioSeeder.seedBundled(production, db, runId) }.exceptionOrNull()
        assertEquals(Phase11AIsolation.PRODUCTION_ERROR, error?.message)
        assertTrue(db.propertyDao().getAllProperties().isEmpty())
        assertTrue(context.getSharedPreferences("phase11a_seed_journal", 0).all.isEmpty())
    }

    @Test fun orphanOrExistingDomainPreferenceRefusesSeedAndIsPreserved() = runBlocking {
        val prefs = context.getSharedPreferences("rent_plan_prefs", 0)
        prefs.edit().putString("existing", "keep-me").commit()
        assertNotNull(runCatching { Phase11APortfolioSeeder.seedBundled(context, db, runId) }.exceptionOrNull())
        assertEquals("keep-me", prefs.getString("existing", null))
        assertTrue(db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
    }

    @Test fun untouchedUiBootstrapProfileIsPreservedButEditedProfileBlocksSeed() = runBlocking {
        val profile = DatevProfileService.getActiveProfile(context)
        val prefs = context.getSharedPreferences("datev_kanzleiprofil_prefs", 0)
        val raw = prefs.getString("active_profile_json", null)
        Phase11APortfolioSeeder.seedBundled(context, db, runId)
        assertEquals(raw, prefs.getString("active_profile_json", null))
        assertEquals(profile, DatevProfileService.getActiveProfile(context))
    }

    @Test fun editedProfileBlocksSeedWithoutOverwritingIt() = runBlocking {
        val profile = DatevProfile.createDefaultSkr03().copy(mandantenName = "Eigene Eingabe")
        DatevProfileService.saveActiveProfile(context, profile)
        assertNotNull(runCatching { Phase11APortfolioSeeder.seedBundled(context, db, runId) }.exceptionOrNull())
        assertEquals(profile, DatevProfileService.getActiveProfile(context))
        assertTrue(db.propertyDao().getAllProperties().isEmpty())
    }

    @Test fun damagedOriginalFailsBeforeAnyDomainWrite() = runBlocking {
        val input = File(context.cacheDir, "damaged-input.zip")
        val source = File(context.cacheDir, "source.zip")
        context.assets.open(Phase11APortfolioSeeder.ASSET).use { a -> source.outputStream().use { a.copyTo(it) } }
        ZipFile(source).use { zip -> ZipOutputStream(input.outputStream()).use { out ->
            zip.entries().asSequence().forEach { entry ->
                out.putNextEntry(ZipEntry(entry.name))
                out.write(if (entry.name.startsWith("originals/")) byteArrayOf(1, 2, 3) else zip.getInputStream(entry).use { it.readBytes() })
                out.closeEntry()
            }
        } }
        assertNotNull(runCatching { Phase11APortfolioSeeder.seed(context, db, input, StableDocumentIdentity.sha256(input.readBytes()), runId) }.exceptionOrNull())
        assertTrue(db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertTrue(context.getSharedPreferences("phase11a_seed_journal", 0).all.isEmpty())
        assertFalse(File(context.filesDir, "managed_documents").exists())
    }

    @Test fun roomFailureRollsBackAllRowsAndPendingJournalBlocksRetry() = runBlocking {
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER seed_test_abort BEFORE INSERT ON bank_transactions BEGIN SELECT RAISE(ABORT, 'test failure'); END")
        assertEquals(Phase11APortfolioSeeder.INCOMPLETE,
            runCatching { Phase11APortfolioSeeder.seedBundled(context, db, runId) }.exceptionOrNull()?.message)
        assertTrue(db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertTrue(db.propertyDao().getAllProperties().isEmpty())
        assertEquals("pending", context.getSharedPreferences("phase11a_seed_journal", 0).getString("state", null))
        assertEquals(Phase11APortfolioSeeder.INCOMPLETE,
            runCatching { Phase11APortfolioSeeder.seedBundled(context, db, runId) }.exceptionOrNull()?.message)
    }

    @Test fun originalEntryWithPathLikeExtensionIsRefusedBeforeAnyDomainWrite() = runBlocking {
        val source = File(context.cacheDir, "extension-source.zip")
        val malformed = File(context.cacheDir, "extension-malformed.zip")
        context.assets.open(Phase11APortfolioSeeder.ASSET).use { input -> source.outputStream().use { input.copyTo(it) } }
        ZipFile(source).use { zip -> ZipOutputStream(malformed.outputStream()).use { out ->
            zip.entries().asSequence().forEach { entry ->
                val name = if (entry.name.startsWith("originals/")) entry.name.substringBeforeLast('.') + ".pdf/../../unexpected" else entry.name
                out.putNextEntry(ZipEntry(name))
                out.write(zip.getInputStream(entry).use { it.readBytes() })
                out.closeEntry()
            }
        } }
        assertNotNull(runCatching { Phase11APortfolioSeeder.seed(context, db, malformed, StableDocumentIdentity.sha256(malformed.readBytes()), runId) }.exceptionOrNull())
        assertTrue(db.receiptDao().getAllReceiptsIncludingDeletedList().isEmpty())
        assertTrue(context.getSharedPreferences("phase11a_seed_journal", 0).all.isEmpty())
        assertFalse(File(context.filesDir, "managed_documents").exists())
    }
}
