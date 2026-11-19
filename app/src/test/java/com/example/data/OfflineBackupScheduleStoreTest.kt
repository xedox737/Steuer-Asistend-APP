package com.example.data

import android.content.Context
import android.content.ContextWrapper
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineBackupScheduleStoreTest {
    private lateinit var root: File
    private lateinit var context: Context
    private val now = Instant.parse("2026-11-18T05:00:00Z")
    private val rule = OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.DAILY, LocalTime.of(7, 0), null, ZoneId.of("Europe/Berlin"))
    private val config get() = OfflineBackupScheduleStore.Config(true, rule, "content://synthetic.documents/tree/folder", false)
    private fun store() = OfflineBackupScheduleStore(context)
    private val file get() = File(context.noBackupFilesDir, "offline-backup-state/schedule.json")
    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "schedule-${UUID.randomUUID()}").apply { check(mkdirs()) }
        context = object : ContextWrapper(base) { override fun getNoBackupFilesDir() = root }
    }
    @After fun cleanup() { root.deleteRecursively() }

    @Test fun persistedOccurrenceRepairsMissingRegistrationWithoutDuplicates() {
        val state = store().configure(config, now)
        assertEquals(state, store().load())
        assertEquals(Instant.parse("2026-11-18T06:00:00Z").toEpochMilli(), state.next?.dueMillis)
        assertTrue(store().needsRegistration(state, OfflineBackupScheduleStore.WorkState.MISSING))
        assertFalse(store().needsRegistration(state, OfflineBackupScheduleStore.WorkState.WAITING))
        assertFalse(store().needsRegistration(state, OfflineBackupScheduleStore.WorkState.RUNNING))
        assertFalse(store().needsRegistration(state, OfflineBackupScheduleStore.WorkState.UNKNOWN))
        assertEquals(OfflineBackupScheduleStore.ClaimResult.NOT_DUE,
            store().claim(state.generation, state.next!!.id, now))
        assertEquals(state, store().load())
        val changed = store().configure(config.copy(rule = rule.copy(zone = ZoneId.of("UTC"))), now)
        assertNotEquals(state.generation, changed.generation)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.STALE,
            store().claim(state.generation, state.next!!.id, Instant.ofEpochMilli(state.next!!.dueMillis)))
        assertEquals(changed, store().load())
    }

    @Test fun claimAdvancesOnceAndScheduleChangeDoesNotCancelRunningExport() {
        val initial = store().configure(config, now); val occurrence = requireNotNull(initial.next)
        val start = Instant.ofEpochMilli(occurrence.dueMillis)
        val decision = store().claimForExecution(initial.generation, occurrence.id, start)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.CLAIMED, decision.result)
        assertEquals(config, decision.config)
        val claimed = requireNotNull(store().load()); assertEquals(occurrence, claimed.active?.occurrence)
        assertEquals(Instant.parse("2026-11-19T06:00:00Z").toEpochMilli(), claimed.next?.dueMillis)
        val following = requireNotNull(claimed.next)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.BUSY,
            store().claim(claimed.generation, following.id, Instant.ofEpochMilli(following.dueMillis)))
        assertEquals(claimed, store().load())
        assertEquals(OfflineBackupScheduleStore.ClaimResult.ALREADY_CLAIMED, store().claim(initial.generation, occurrence.id, start))
        val changed = store().configure(config.copy(enabled = false, automaticCredentials = true), start)
        assertNotEquals(initial.generation, changed.generation); assertEquals(claimed.active, changed.active)
        assertFalse(requireNotNull(decision.config).automaticCredentials)
        assertNull(changed.next); assertFalse(store().needsRegistration(changed, OfflineBackupScheduleStore.WorkState.MISSING))
        assertTrue(store().finish(occurrence.id)); assertFalse(store().finish(occurrence.id))
        assertFalse(requireNotNull(store().load()).config.enabled)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.STALE, store().claim(initial.generation, occurrence.id, start))
    }

    @Test fun stoppedClaimIsReportedOnceAndDoesNotCreateCatchUpSeries() {
        val initial = store().configure(config, now); val occurrence = requireNotNull(initial.next)
        val delayed = Instant.parse("2026-11-22T08:00:00Z")
        assertEquals(OfflineBackupScheduleStore.ClaimResult.CLAIMED, store().claim(initial.generation, occurrence.id, delayed))
        val claimed = requireNotNull(store().load())
        assertEquals(Instant.parse("2026-11-23T06:00:00Z").toEpochMilli(), claimed.next?.dueMillis)
        assertFalse(store().reconcileStoppedClaim(occurrence.id, OfflineBackupScheduleStore.WorkState.UNKNOWN))
        assertFalse(store().reconcileStoppedClaim(occurrence.id, OfflineBackupScheduleStore.WorkState.RUNNING))
        assertTrue(store().reconcileStoppedClaim(occurrence.id, OfflineBackupScheduleStore.WorkState.FINISHED))
        val repaired = requireNotNull(store().load()); assertNull(repaired.active)
        assertEquals(occurrence, repaired.lastInterrupted?.occurrence)
        assertFalse(store().reconcileStoppedClaim(occurrence.id, OfflineBackupScheduleStore.WorkState.FINISHED))
        assertEquals(OfflineBackupScheduleStore.ClaimResult.STALE, store().claim(initial.generation, occurrence.id, delayed))
        assertTrue(store().needsRegistration(repaired, OfflineBackupScheduleStore.WorkState.MISSING))
    }

    @Test fun verifiedClaimIsNotMisreportedAndCorruptionNeverResetsSchedule() {
        val initial = store().configure(config, now); val occurrence = requireNotNull(initial.next)
        val start = Instant.ofEpochMilli(occurrence.dueMillis)
        assertEquals(OfflineBackupScheduleStore.ClaimResult.CLAIMED, store().claim(initial.generation, occurrence.id, start))
        val event = OfflineBackupHistory.Event(occurrence.id, OfflineBackupHistory.Kind.FULL, OfflineBackupHistory.Outcome.VERIFIED,
            start.toEpochMilli(), 10, null, OfflineBackupHistory.Origin.AUTOMATIC, UUID.randomUUID().toString(),
            "content://synthetic.documents/document/archive", 1, 2)
        OfflineBackupHistory(context).record(event)
        assertTrue(store().reconcileStoppedClaim(occurrence.id, OfflineBackupScheduleStore.WorkState.FINISHED))
        assertNull(store().load()?.lastInterrupted)
        val expected = store().load()
        val partial = AtomicFile(file).startWrite(); partial.write("[1,".toByteArray()); partial.fd.sync(); partial.close()
        assertEquals(expected, store().load())
        file.writeText("[broken")
        assertThrows(OfflineBackupValidationException::class.java) { store().load() }
        assertThrows(OfflineBackupValidationException::class.java) { store().configure(config, now) }
        assertEquals("[broken", file.readText())
    }
}
