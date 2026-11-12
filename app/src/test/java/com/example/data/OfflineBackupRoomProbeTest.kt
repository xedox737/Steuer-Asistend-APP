package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupRoomProbeTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private lateinit var first: File
    private lateinit var second: File
    private val namespace = "probe-test-${UUID.randomUUID()}"
    @Before fun setup(): Unit = runBlocking {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getFilesDir() = File(root, "files").apply { check(isDirectory || mkdirs()) }
            override fun getCacheDir() = File(root, "cache").apply { check(isDirectory || mkdirs()) }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        first = File(context.filesDir, "managed_documents/first.pdf").apply { parentFile!!.mkdirs(); writeText("%PDF-1.4 first original") }
        second = File(context.filesDir, "managed_documents/second.pdf").apply { writeText("%PDF-1.4 second original") }
        context.getSharedPreferences("wohneinheiten_prefs", 0).edit().putString("property_house_unit_Wohnung_id", "unit-a").commit()
        context.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "archived note")
            .putFloat("typed-float", 1.25f).putStringSet("typed-set", setOf("alpha", "beta")).commit()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Archived house", wohneinheiten = "Wohnung"))
        database.receiptDao().insertReceipt(Receipt(id = 41, internalId = "stable-receipt", propertyId = "house", unitId = "unit-a",
            aussteller = "Archived", datum = "2026-11-11", uhrzeit = "", bruttobetrag = 125.73, hauptkategorie = "Kosten",
            unterkategorie = "Reparatur", kontoNr = "4800", beschreibung = "archived description",
            imageUrl = JSONArray(listOf(first.absolutePath, second.absolutePath)).toString()))
        database.managedDocumentDao().upsert(ManagedDocument("document", "house", unitId = "unit-a", receiptInternalId = "stable-receipt",
            title = "Archived original", localUri = first.absolutePath, originalFilename = "first.pdf", storedFilename = "first.pdf"))
        database.openHelper.writableDatabase.execSQL("UPDATE sqlite_sequence SET seq=1000 WHERE name='receipts'")
    }
    @After fun cleanup() {
        database.close()
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
        (it.toInt() and 255).toString(16).padStart(2, '0') }
    private suspend fun contents(change: (JSONObject) -> Unit = {}, changeManifest: (JSONObject) -> Unit = {}): OfflineBackupArchiveValidator.StagedContents {
        val raw = ByteArrayOutputStream()
        OfflineBackupSnapshot.capture(context, database).use { it.writeArchive(raw) }
        val entries = ZipInputStream(raw.toByteArray().inputStream()).use { zip -> linkedMapOf<String, ByteArray>().apply {
            while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) }
        } }
        val metadata = JSONObject(entries.getValue("metadata.json").toString(Charsets.UTF_8)); change(metadata)
        val bytes = metadata.toString().toByteArray(); entries["metadata.json"] = bytes
        val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
        manifest.getJSONObject("metadata").put("size", bytes.size).put("sha256", hash(bytes))
        metadata.getJSONObject("tables").keys().forEach { table ->
            manifest.getJSONObject("rowCounts").put(table, metadata.getJSONObject("tables").getJSONObject(table).getJSONArray("rows").length())
        }
        changeManifest(manifest); entries["manifest.json"] = manifest.toString().toByteArray()
        val source = File(root, "input.zip")
        ZipOutputStream(source.outputStream()).use { zip -> entries.forEach { (name, value) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(value); zip.closeEntry()
        } }
        return OfflineBackupArchiveValidator.stageZip(context, source)
    }
    private fun cell(metadata: JSONObject, table: String, column: String, value: Any) {
        val data = metadata.getJSONObject("tables").getJSONObject(table)
        val columns = data.getJSONArray("columns")
        val index = (0 until columns.length()).single { columns.getJSONObject(it).getString("name") == column }
        data.getJSONArray("rows").getJSONArray(0).put(index, value)
    }
    private fun noProbeLeft(contents: OfflineBackupArchiveValidator.StagedContents) {
        val directory = contents.parts.getValue("metadata.json").file!!.parentFile!!
        assertTrue(directory.listFiles()!!.none { it.name.startsWith("probe-") })
    }

    @Test fun restoresAllTablesInIsolatedRoomWithArchivedUnitsOriginalsIdsAndSequence() = runBlocking<Unit> {
        contents().use { staged ->
            val liveUnits = context.getSharedPreferences("wohneinheiten_prefs", 0)
            liveUnits.edit().putString("property_house_unit_Wohnung_id", "unit-live").commit()
            val before = liveUnits.all.toMap()
            val capture = OfflineBackupCoordinator.shared.beginCapture()
            OfflineBackupRoomProbe.prepare(context, staged).use { probe ->
                assertEquals(18, probe.manifest.rowCounts.size)
                val restored = probe.database.receiptDao().getAllReceiptsIncludingDeletedList().single()
                assertEquals(41, restored.id); assertEquals("stable-receipt", restored.internalId)
                assertEquals("unit-a", restored.unitId); assertEquals("archived description", restored.beschreibung)
                val pages = JSONArray(restored.imageUrl)
                assertEquals(2, pages.length())
                assertArrayEquals(first.readBytes(), File(pages.getString(0)).readBytes())
                assertArrayEquals(second.readBytes(), File(pages.getString(1)).readBytes())
                val doc = probe.database.managedDocumentDao().getAll().single()
                assertEquals("document", doc.documentId); assertArrayEquals(first.readBytes(), File(doc.localUri).readBytes())
                probe.database.receiptDao().insertReceipt(restored.copy(id = 0, internalId = "probe-next-receipt"))
                assertEquals(1001, probe.database.receiptDao().getAllReceiptsIncludingDeletedList().single { it.internalId == "probe-next-receipt" }.id)
            }
            OfflineBackupCoordinator.shared.completeCapture(capture)
            assertEquals(before, liveUnits.all); assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
            noProbeLeft(staged)
        }
    }

    @Test fun schemaTypesDuplicateIdsAndRowCountMismatchRejectWithoutChangingLiveData() = runBlocking<Unit> {
        val variants = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("tables").getJSONObject("receipts").getJSONArray("columns").getJSONObject(0).put("type", "TEXT"); Unit },
            { cell(it, "receipts", "id", "41") },
            { cell(it, "receipts", "internalId", JSONObject.NULL) },
            { it.getJSONObject("tables").getJSONObject("receipts").getJSONArray("rows").put(
                it.getJSONObject("tables").getJSONObject("receipts").getJSONArray("rows").getJSONArray(0)); Unit },
            { it.getJSONObject("sequences").put("bank_accounts", 4); Unit }
        )
        variants.forEach { mutate -> contents(mutate).use { staged ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupRoomProbe.prepare(context, staged) } }
            noProbeLeft(staged)
        } }
        contents(changeManifest = { it.getJSONObject("rowCounts").put("receipts", 2) }).use { staged ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupRoomProbe.prepare(context, staged) } }
            noProbeLeft(staged)
        }
        assertEquals(41, database.receiptDao().getAllReceiptsIncludingDeletedList().single().id)
        assertEquals("%PDF-1.4 first original", first.readText())
    }

    @Test fun sharedBusinessRelationshipsAndOriginalHashAreCheckedInsideProbeOnly() = runBlocking<Unit> {
        val variants = listOf<Pair<(JSONObject) -> Unit, String>>(
            ({ metadata: JSONObject -> cell(metadata, "receipts", "unitId", "foreign-unit") }) to "Objekt- oder Einheitenbeziehung",
            ({ metadata: JSONObject -> cell(metadata, "managed_documents", "receiptInternalId", "missing-receipt") }) to "fehlenden Beleg",
            ({ metadata: JSONObject -> cell(metadata, "managed_documents", "sha256", "0".repeat(64)) }) to "Prüfsumme"
        )
        variants.forEach { (mutate, message) -> contents(mutate).use { staged ->
            val error = assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupRoomProbe.prepare(context, staged) } }
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message)); noProbeLeft(staged)
        } }
        assertEquals("stable-receipt", database.receiptDao().getAllReceiptsIncludingDeletedList().single().internalId)
    }

    @Test fun forbiddenPreferenceKeysAndMissingStoresAreRejectedWithoutApplyingPreferences() = runBlocking<Unit> {
        val before = context.getSharedPreferences("bank_transaction_notes", 0).all.toMap()
        val variants = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("preferences").getJSONObject(PersistentPreferenceInventory.stores.single { s -> s.name == "ai_provider_settings" }.payloadKey!!).put("openai_key_ciphertext",
                JSONObject().put("type", "string").put("value", "synthetic-only")); Unit },
            { val key = PersistentPreferenceInventory.stores.single { s -> s.name == "wohneinheiten_prefs" }.payloadKey!!
                it.getJSONObject("preferences").remove(key); Unit }
        )
        variants.forEach { mutate -> contents(mutate).use { staged ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupRoomProbe.prepare(context, staged) } }
            noProbeLeft(staged)
        } }
        assertEquals(before, context.getSharedPreferences("bank_transaction_notes", 0).all)
    }

    @Test fun cancelledProbeDoesNotBecomeValidationFailureOrLeaveDatabase() = runBlocking<Unit> {
        contents().use { staged ->
            assertThrows(kotlinx.coroutines.CancellationException::class.java) { runBlocking {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel(); OfflineBackupRoomProbe.prepare(context, staged)
            } }
            noProbeLeft(staged)
        }
    }
    @Test fun changedPrivateStagingCannotPassProbeEvenWhenBusinessDataStillLooksValid() = runBlocking<Unit> {
        for (entry in listOf("manifest.json", "metadata.json", "files/0")) {
            contents().use { staged ->
                val file = staged.parts.getValue(entry).file!!
                when (entry) {
                    "manifest.json" -> {
                        val changed = JSONObject(file.readText())
                        changed.getJSONObject("aliases").put("original-0", "files/1")
                        file.writeText(changed.toString())
                    }
                    "metadata.json" -> {
                        val raw = file.readText(); val changed = raw.replace("archived description", "tampered description")
                        assertNotEquals(raw, changed); assertEquals(raw.length, changed.length); file.writeText(changed)
                    }
                    else -> file.writeBytes(file.readBytes().apply { this[0] = (this[0].toInt() xor 1).toByte() })
                }
                assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineBackupRoomProbe.prepare(context, staged).close() } }
                noProbeLeft(staged)
            }
        }
        assertEquals("archived description", database.receiptDao().getAllReceiptsIncludingDeletedList().single().beschreibung)
    }

}
