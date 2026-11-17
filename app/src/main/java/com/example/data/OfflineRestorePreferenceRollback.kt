package com.example.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Replays only the verified business preferences; the caller retains the recovery journal. */
internal object OfflineRestorePreferenceRollback {
    suspend fun apply(context: Context, token: OfflineBackupCoordinator.Maintenance, entry: OfflineRestoreJournal.Entry) {
        try {
            OfflineBackupCoordinator.shared.requireMaintenance(token)
            require(entry.phase == OfflineRestoreJournal.Phase.ROLLBACK)
            withContext(Dispatchers.IO) {
                token.suspending {
                    val checked = OfflineRestorePreferenceVerifier.verify(context, entry)
                    val journal = OfflineRestoreJournal(context)
                    PersistentPreferenceInventory.stores.filter { it.kind == PersistentPreferenceInventory.Kind.BACKUP }.forEach { store ->
                        currentCoroutineContext().ensureActive()
                        require(journal.load() == entry)
                        val prefs = PersistentPreferenceInventory.open(context, store.name)
                        val values = checked.payload.getJSONObject(requireNotNull(store.payloadKey))
                        val editor = prefs.edit()
                        prefs.all.keys.filter { PersistentPreferenceInventory.permits(store, it) }.forEach(editor::remove)
                        values.keys().forEach { key ->
                            require(PersistentPreferenceInventory.permits(store, key))
                            val item = values.getJSONObject(key)
                            when (item.getString("type")) {
                                "string" -> editor.putString(key, item.getString("value"))
                                "int" -> editor.putInt(key, item.getInt("value"))
                                "long" -> editor.putLong(key, item.getLong("value"))
                                "float" -> editor.putFloat(key, item.getDouble("value").toFloat())
                                "boolean" -> editor.putBoolean(key, item.getBoolean("value"))
                                "stringSet" -> item.getJSONArray("value").let { array ->
                                    editor.putStringSet(key, (0 until array.length()).map { array.getString(it) }.toSet())
                                }
                                else -> error("Ungültiger Preference-Typ.")
                            }
                        }
                        check(editor.commit())
                    }
                    OfflineRestoreLivePreferences.verifyRestored(context, checked.payload)
                    require(journal.load() == entry)
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: OfflineBackupValidationException) { throw error }
        catch (_: IllegalArgumentException) { invalid() }
        catch (_: IllegalStateException) { invalid() }
        catch (_: SecurityException) { invalid() }
        catch (_: org.json.JSONException) { invalid() }
    }

    private fun invalid(): Nothing = throw OfflineBackupValidationException(
        "Die Einstellungen konnten nicht vollständig zurückgesetzt werden. Der Rückfall bleibt gesperrt und kann erneut versucht werden."
    )
}
