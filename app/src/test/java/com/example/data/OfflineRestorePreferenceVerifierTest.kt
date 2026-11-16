package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
class OfflineRestorePreferenceVerifierTest {
    private lateinit var context: Context
    private lateinit var root: File
    private lateinit var database: AppDatabase
    private val namespace = "rollback-prefs-verify-${UUID.randomUUID()}"
    private fun id() = UUID.randomUUID().toString()
    private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
    @Before fun setup() = runBlocking<Unit> {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            private fun dir(name: String) = File(root, name).apply { check(isDirectory || mkdirs()) }
            override fun getFilesDir() = dir("files")
            override fun getCacheDir() = dir("cache")
            override fun getNoBackupFilesDir() = dir("no-backup")
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).openHelperFactory(OfflineBackupCoordinator.shared.sqliteFactory()).build()
        database.propertyDao().insertPropertyMetadata(PropertyMetadata(id = 3, propertyId = "house", name = "Haus"))
        context.getSharedPreferences("bank_transaction_notes", 0).edit().putString("note", "original").putInt("count", 4)
            .putLong("large", 5_000_000_000L).putFloat("float", 1.25f).putBoolean("flag", true)
            .putStringSet("set", setOf("a", "z")).putString("api_key", "SYNTHETIC_SECRET").commit()
        context.getSharedPreferences("rent_plan_prefs", 0).edit().putFloat("nk_house", -3.5f).commit()
    }
    @After fun cleanup() {
        database.close(); PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    private suspend fun bundle() = OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
        OfflineRestoreRollbackBundle.prepare(context, database, token)
    }
    private fun bind(bundle: OfflineRestoreRollbackBundle.Prepared): OfflineRestoreJournal.Entry =
        OfflineRestoreJournal(context).begin(id(), id(), bundle.id, bundle.manifest.length(), hash(bundle.manifest))
    private fun rebind(bundle: OfflineRestoreRollbackBundle.Prepared) {
        val master = JSONObject(bundle.manifest.readText()).put("preferencesBytes", bundle.preferences.file.length())
            .put("preferencesSha256", hash(bundle.preferences.file))
        bundle.manifest.writeText(master.toString())
    }
    @Test fun preservesOriginalTypesAndHistoricalValuesWithoutWritingLivePreferences() = runBlocking<Unit> {
        val bundle = bundle(); val journal = bind(bundle)
        val result = OfflineRestorePreferenceVerifier.verify(context, journal).payload
        val notes = result.getJSONObject("bankTransactionNotes")
        assertEquals("original", notes.getJSONObject("note").getString("value"))
        assertEquals(4, notes.getJSONObject("count").getInt("value"))
        assertEquals(5_000_000_000L, notes.getJSONObject("large").getLong("value"))
        assertEquals(1.25, notes.getJSONObject("float").getDouble("value"), 0.0)
        assertTrue(notes.getJSONObject("flag").getBoolean("value"))
        assertEquals("[\"a\",\"z\"]", notes.getJSONObject("set").getJSONArray("value").toString())
        assertEquals(-3.5, result.getJSONObject("rentPlanPrefs").getJSONObject("nk_house").getDouble("value"), 0.0)
        assertFalse(notes.has("api_key")); assertEquals("SYNTHETIC_SECRET", context.getSharedPreferences("bank_transaction_notes", 0).getString("api_key", null))
        assertEquals("original", context.getSharedPreferences("bank_transaction_notes", 0).getString("note", null))
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
    @Test fun rejectsForbiddenSecretEvenWhenHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = bundle.preferences.file
        val payload = JSONObject(file.readText()).apply {
            getJSONObject("bankTransactionNotes").put("api_key", JSONObject().put("type", "string").put("value", "SYNTHETIC_SECRET"))
        }
        file.writeText(payload.toString()); rebind(bundle); val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
    @Test fun rejectsUnknownStoreEvenWhenHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = bundle.preferences.file
        val raw = file.readText(); file.writeText(raw.replaceFirst("{", "{\"unknownStore\":{},"))
        rebind(bundle); val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
    @Test fun rejectsMissingStoreEvenWhenHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = bundle.preferences.file
        val payload = JSONObject(file.readText()); payload.remove("aiSelectionPrefs")
        file.writeText(payload.toString()); rebind(bundle); val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }
    @Test fun rejectsDuplicateStoreEvenWhenHashesMatch() = runBlocking<Unit> {
        val bundle = bundle(); val file = bundle.preferences.file
        val raw = file.readText(); assertTrue(raw.contains("\"rentPlanPrefs\":"))
        file.writeText(raw.replaceFirst("\"rentPlanPrefs\":", "\"rentPlanPrefs\":{},\"rentPlanPrefs\":"))
        rebind(bundle); val journal = bind(bundle)
        OfflineRestoreRollbackVerifier.verifyIndex(context, journal)
        assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceVerifier.verify(context, journal) } }
        assertEquals(journal, OfflineRestoreJournal(context).load())
    }

    @Test fun rollbackRestoresOriginalTypesAndIsRepeatableWithoutChangingSecrets() = runBlocking<Unit> {
        val bundle = bundle(); val prepared = bind(bundle)
        val journal = OfflineRestoreJournal(context)
        val rollback = journal.advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
        val notes = context.getSharedPreferences("bank_transaction_notes", 0)
        notes.edit().putString("note", "changed").putInt("new", 9).putString("api_key", "current secret").commit()
        context.getSharedPreferences("rent_plan_prefs", 0).edit().putFloat("nk_house", 2f).commit()
        val local = context.getSharedPreferences("app_installation_prefs", 0)
        local.edit().putString("device", "keep").commit()
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            repeat(2) { OfflineRestorePreferenceRollback.apply(context, token, rollback) }
        }
        assertEquals("original", notes.getString("note", null))
        assertEquals(4, notes.getInt("count", 0)); assertEquals(5_000_000_000L, notes.getLong("large", 0))
        assertEquals(1.25f, notes.getFloat("float", 0f), 0f); assertTrue(notes.getBoolean("flag", false))
        assertEquals(setOf("a", "z"), notes.getStringSet("set", emptySet()))
        assertFalse(notes.contains("new")); assertEquals("current secret", notes.getString("api_key", null))
        assertEquals(-3.5f, context.getSharedPreferences("rent_plan_prefs", 0).getFloat("nk_house", 0f), 0f)
        assertEquals("keep", local.getString("device", null)); assertEquals(rollback, journal.load())
        local.edit().clear().commit()
    }
    @Test fun rollbackRefusesWrongPhaseForeignMaintenanceAndDamagedSnapshotBeforeWriting() = runBlocking<Unit> {
        val bundle = bundle(); val prepared = bind(bundle)
        val notes = context.getSharedPreferences("bank_transaction_notes", 0)
        notes.edit().putString("note", "current").commit()
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestorePreferenceRollback.apply(context, token, prepared)
            } }
        }
        val rollback = OfflineRestoreJournal(context).advance(prepared, OfflineRestoreJournal.Phase.ROLLBACK)
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
            OfflineRestorePreferenceRollback.apply(context, foreign, rollback)
        } } } finally { foreign.close() }
        bundle.preferences.file.appendText(" ")
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking {
                OfflineRestorePreferenceRollback.apply(context, token, rollback)
            } }
        }
        assertEquals("current", notes.getString("note", null))
        assertEquals(rollback, OfflineRestoreJournal(context).load())
    }

}
