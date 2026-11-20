package com.example.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class OfflineBackupScheduleTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun daily(hour: Int, minute: Int, zone: ZoneId = berlin) = OfflineBackupSchedule.Rule(
        OfflineBackupSchedule.Cadence.DAILY, LocalTime.of(hour, minute), null, zone)

    @Test fun dailyAndWeeklyUseTheNextLocalCalendarOccurrence() {
        val after = Instant.parse("2026-12-31T22:00:00Z")
        assertEquals(Instant.parse("2027-01-01T06:00:00Z"), OfflineBackupSchedule.next(daily(7, 0), after))
        val weekly = OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.WEEKLY, LocalTime.of(7, 0), DayOfWeek.FRIDAY, berlin)
        assertEquals(Instant.parse("2027-01-01T06:00:00Z"), OfflineBackupSchedule.next(weekly, after))
        assertEquals(Instant.parse("2027-01-08T06:00:00Z"), OfflineBackupSchedule.next(weekly, Instant.parse("2027-01-01T06:00:00Z")))
    }

    @Test fun springGapMovesForwardByTheTransition() {
        assertEquals(Instant.parse("2026-03-29T01:30:00Z"),
            OfflineBackupSchedule.next(daily(2, 30), Instant.parse("2026-03-28T23:00:00Z")))
    }

    @Test fun autumnOverlapRunsOnlyAtTheEarlierOccurrence() {
        val rule = daily(2, 30)
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), OfflineBackupSchedule.next(rule, Instant.parse("2026-10-24T22:00:00Z")))
        assertEquals(Instant.parse("2026-10-26T01:30:00Z"), OfflineBackupSchedule.next(rule, Instant.parse("2026-10-25T00:45:00Z")))
    }

    @Test fun timezoneChangeRecomputesTheSameLocalTimeAndInvalidRulesAreRejected() {
        val after = Instant.parse("2026-11-18T05:00:00Z")
        assertEquals(Instant.parse("2026-11-18T06:00:00Z"), OfflineBackupSchedule.next(daily(7, 0), after))
        assertEquals(Instant.parse("2026-11-18T07:00:00Z"), OfflineBackupSchedule.next(daily(7, 0, ZoneId.of("UTC")), after))
        assertThrows(IllegalArgumentException::class.java) {
            OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.WEEKLY, LocalTime.of(7, 0), null, berlin)
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineBackupSchedule.Rule(OfflineBackupSchedule.Cadence.DAILY, LocalTime.of(7, 0, 1), null, berlin)
        }
    }
}
