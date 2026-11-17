package com.example.data

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** Restores validated business preferences while keeping device-only and secret fields. */
internal object OfflineRestoreLivePreferences {
    suspend fun apply(context: Context, token: OfflineBackupCoordinator.Maintenance,
        entry: OfflineRestoreJournal.Entry, archive: OfflineBackupRestoreInspector.Prepared) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.PREFERENCES)
            withContext(Dispatchers.IO) { token.suspending {
                OfflineRestoreDatabaseVerifier.verify(context, entry)
                val plan = OfflineRestoreImportedFiles.verifyInstalled(context, token, entry)
                OfflineBackupRoomProbe.prepare(context, archive.contents, archive.generation).use { probe ->
                    require(OfflineRestoreFilePlan.prepare(context, entry.operationId, probe.manifest, requireUnused = false) == plan)
                    // An existing Drive connection must never start syncing restored data automatically.
                    val values = probe.preferences
                    values.getJSONObject("appSettingPrefs").put("auto_backup", JSONObject().put("type", "boolean").put("value", false))
                    PersistentPreferenceInventory.validatePayload(values)
                    require(OfflineRestoreJournal(context).load() == entry)
                    currentCoroutineContext().ensureActive()
                    PersistentPreferenceInventory.restorePayload(context, values, RestoreMode.REPLACE_FULL)
                    verify(context, values)
                    require(OfflineRestoreImportedFiles.verifyInstalled(context, token, entry) == plan)
                    require(OfflineRestoreJournal(context).load() == entry)
                }
            } }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IOException) { invalid() }
        catch (_: JSONException) { invalid() }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: NoSuchElementException) { invalid() }
        catch (_: SecurityException) { invalid() }
    }

    private suspend fun verify(context: Context, payload: JSONObject) {
        PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }.forEach { store ->
            currentCoroutineContext().ensureActive()
            val expected = payload.getJSONObject(requireNotNull(store.payloadKey))
            val actual = PersistentPreferenceInventory.open(context, store.name).all.filterKeys { PersistentPreferenceInventory.permits(store, it) }
            require(actual.keys == expected.keys().asSequence().toSet())
            expected.keys().forEach { key ->
                val item = expected.getJSONObject(key); val current = actual.getValue(key)
                require(when (item.getString("type")) {
                    "string" -> current is String && current == item.getString("value")
                    "int" -> current is Int && current == item.getInt("value")
                    "long" -> current is Long && current == item.getLong("value")
                    "float" -> current is Float && current.toBits() == item.getDouble("value").toFloat().toBits()
                    "boolean" -> current is Boolean && current == item.getBoolean("value")
                    "stringSet" -> item.getJSONArray("value").let { array ->
                        current is Set<*> && current == (0 until array.length()).map { array.getString(it) }.toSet()
                    }
                    else -> false
                })
            }
        }
    }
    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Einstellungen konnten nicht vollständig wiederhergestellt werden. Der Rückfall bleibt gesperrt und kann ausgeführt werden."
    )
}
