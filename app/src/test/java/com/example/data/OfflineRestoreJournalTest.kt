package com.example.data

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 35])
class OfflineRestoreJournalTest {
    private lateinit var root: File
    private lateinit var context: Context
    private fun id() = UUID.randomUUID().toString()
    private val file get() = File(context.noBackupFilesDir, "offline-backup-state/restore-journal.json")
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "journal-test-${id()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
    }
    @After fun cleanup() { root.deleteRecursively() }
    @Test fun phasesSurviveNewInstancesAndOnlyTerminalStateCanBeCleared() {
        var journal = OfflineRestoreJournal(context)
        assertNull(journal.load())
        var entry = journal.begin(id(), id(), id())
        assertEquals(entry, OfflineRestoreJournal(context).load())
        assertThrows(OfflineBackupValidationException::class.java) { journal.clear(entry) }
        for (phase in listOf(OfflineRestoreJournal.Phase.FILES, OfflineRestoreJournal.Phase.DATABASE,
            OfflineRestoreJournal.Phase.PREFERENCES, OfflineRestoreJournal.Phase.COMMITTED)) {
            journal = OfflineRestoreJournal(context); entry = journal.advance(entry, phase)
            assertEquals(entry, OfflineRestoreJournal(context).load())
        }
        journal.clear(entry); assertNull(OfflineRestoreJournal(context).load())
    }
    @Test fun staleOperationsAndInvalidTransitionsNeverReplaceCurrentJournal() {
        val journal = OfflineRestoreJournal(context); val entry = journal.begin(id(), id(), id())
        assertThrows(OfflineBackupValidationException::class.java) { journal.begin(id(), id(), id()) }
        assertThrows(OfflineBackupValidationException::class.java) { journal.advance(entry, OfflineRestoreJournal.Phase.COMMITTED) }
        assertThrows(OfflineBackupValidationException::class.java) { journal.advance(entry.copy(operationId = id()), OfflineRestoreJournal.Phase.FILES) }
        assertEquals(entry, journal.load())
        val rollback = journal.advance(entry, OfflineRestoreJournal.Phase.ROLLBACK)
        val done = journal.advance(rollback, OfflineRestoreJournal.Phase.ROLLED_BACK)
        journal.clear(done); assertNull(journal.load())
    }
    @Test fun malformedDuplicateUnknownOversizedAndOrphanedRecordsNeverLookAbsent() {
        val journal = OfflineRestoreJournal(context); val entry = journal.begin(id(), id(), id())
        val valid = file.readText()
        val examples = listOf("{", valid.replace("\"version\":1", "\"version\":1,\"version\":1"),
            valid.replace("local-full-restore", "drive"), valid.replace("PREPARED", "UNKNOWN"), "x".repeat(8193))
        examples.forEach { content ->
            file.writeText(content)
            assertThrows(OfflineBackupValidationException::class.java) { journal.load() }
            assertThrows(OfflineBackupValidationException::class.java) { journal.begin(id(), id(), id()) }
            assertEquals(content, file.readText())
        }
        file.writeText(valid); assertEquals(entry, journal.load())
        file.delete(); File(file.path + ".new").writeText("partial")
        assertThrows(OfflineBackupValidationException::class.java) { journal.load() }
    }
}
