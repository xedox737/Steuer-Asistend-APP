package com.example.data

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/**
 * Durable journal storage for resumable duplicate cleanup operations.
 * Each operation is stored independently so an app/process restart can resume safely.
 */
class SharedPreferencesDuplicateCleanupJournalStore(context: Context) : DuplicateCleanupJournalStore {
    private val prefs = context.applicationContext.getSharedPreferences(
        "duplicate_cleanup_journals",
        Context.MODE_PRIVATE
    )
    private val adapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(DuplicateCleanupJournal::class.java)

    override suspend fun load(operationId: String): DuplicateCleanupJournal? {
        val json = prefs.getString(key(operationId), null) ?: return null
        return adapter.fromJson(json)
    }

    override suspend fun save(journal: DuplicateCleanupJournal) {
        val json = adapter.toJson(journal)
        check(prefs.edit().putString(key(journal.operationId), json).commit()) {
            "Bereinigungsjournal konnte nicht dauerhaft gespeichert werden."
        }
    }

    fun listOperationIds(): Set<String> = prefs.all.keys
        .filter { it.startsWith(PREFIX) }
        .map { it.removePrefix(PREFIX) }
        .toSet()

    fun removeCompleted(operationId: String) {
        val journal = prefs.getString(key(operationId), null)?.let(adapter::fromJson) ?: return
        require(journal.phase == DuplicateCleanupPhase.COMPLETED) {
            "Nur abgeschlossene Bereinigungsjournale dürfen entfernt werden."
        }
        prefs.edit().remove(key(operationId)).apply()
    }

    private fun key(operationId: String) = "$PREFIX$operationId"

    private companion object {
        const val PREFIX = "operation_"
    }
}
