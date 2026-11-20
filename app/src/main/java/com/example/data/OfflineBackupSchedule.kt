package com.example.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Calendar calculation only; persistence and work registration are separate responsibilities. */
internal object OfflineBackupSchedule {
    enum class Cadence { DAILY, WEEKLY }
    data class Rule(val cadence: Cadence, val time: LocalTime, val day: DayOfWeek?, val zone: ZoneId) {
        init {
            require(time.second == 0 && time.nano == 0)
            require((cadence == Cadence.WEEKLY) == (day != null))
        }
    }

    /** First calendar occurrence strictly after [after], at most once per local date. */
    fun next(rule: Rule, after: Instant): Instant {
        var date = after.atZone(rule.zone).toLocalDate()
        repeat(8) {
            if (rule.cadence == Cadence.DAILY || date.dayOfWeek == rule.day) {
                // java.time shifts a gap forward by its length and chooses the earlier
                // offset in an overlap. Never schedule the repeated local time twice.
                // https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/time/ZonedDateTime.html#of(java.time.LocalDateTime,java.time.ZoneId)
                val candidate = LocalDateTime.of(date, rule.time).atZone(rule.zone).toInstant()
                if (candidate.isAfter(after)) return candidate
            }
            date = date.plusDays(1)
        }
        throw IllegalStateException("Der nächste Sicherungstermin konnte nicht berechnet werden.")
    }
}
