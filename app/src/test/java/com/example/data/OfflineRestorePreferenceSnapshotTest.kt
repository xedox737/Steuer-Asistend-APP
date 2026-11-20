package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import java.io.File
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
@Config(sdk = [24, 35])
class OfflineRestorePreferenceSnapshotTest {
    private lateinit var context: Context
    private lateinit var root: File
    private val namespace = "rollback-prefs-${UUID.randomUUID()}"
    private fun id() = UUID.randomUUID().toString()
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, namespace).apply { check(mkdirs()) }
        context = object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences("${namespace}_$name", mode)
        }
    }
    @After fun cleanup() {
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        root.deleteRecursively()
    }
    @Test fun snapshotMatchesInventoryTypesAndExcludesSecretFields() = runBlocking<Unit> {
        val notes = context.getSharedPreferences("bank_transaction_notes", 0)
        notes.edit().putString("note", "Behalten").putInt("int", 4).putLong("long", 5000000000L)
            .putFloat("float", 1.25f).putBoolean("flag", true).putStringSet("set", setOf("z", "a"))
            .putString("api_key", "SYNTHETIC_SECRET_VALUE").commit()
        context.getSharedPreferences("ai_provider_settings", 0).edit().putString("receipt_analysis_provider", "GEMINI")
            .putString("gemini_key_ciphertext", "SYNTHETIC_CIPHERTEXT").commit()
        val expected = JSONObject().also { PersistentPreferenceInventory.writePayload(context, it) }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            val snapshot = OfflineRestorePreferenceSnapshot.capture(context, token, id())
            val raw = snapshot.file.readText(); val actual = JSONObject(raw)
            assertFalse(raw.contains("SYNTHETIC_SECRET_VALUE")); assertFalse(raw.contains("SYNTHETIC_CIPHERTEXT"))
            assertEquals(expected.keys().asSequence().toSet(), actual.keys().asSequence().toSet())
            expected.keys().forEach { store ->
                val left = expected.getJSONObject(store); val right = actual.getJSONObject(store)
                assertEquals(left.keys().asSequence().toSet(), right.keys().asSequence().toSet())
                left.keys().forEach { key -> assertEquals(left.getJSONObject(key).toString(), right.getJSONObject(key).toString()) }
            }
            assertEquals(snapshot.file.length(), snapshot.size); assertEquals(64, snapshot.sha256.length)
            assertEquals("SYNTHETIC_SECRET_VALUE", notes.getString("api_key", null))
        }
    }
    @Test fun existingFileAndInvalidTokenNeverOverwritePriorData() = runBlocking<Unit> {
        val snapshotId = id(); val directory = File(root, "offline-backup-state").apply { mkdirs() }
        val prior = File(directory, "rollback-$snapshotId.preferences.json").apply { writeText("keep") }
        OfflineBackupCoordinator.shared.beginMaintenance().use { token ->
            assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceSnapshot.capture(context, token, snapshotId) } }
        }
        val foreign = OfflineBackupCoordinator().beginMaintenance()
        try { assertThrows(OfflineBackupValidationException::class.java) { runBlocking { OfflineRestorePreferenceSnapshot.capture(context, foreign, id()) } } }
        finally { foreign.close() }
        assertEquals("keep", prior.readText()); assertEquals(listOf(prior.name), directory.list()!!.toList())
    }
}
