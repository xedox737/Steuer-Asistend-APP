package com.example.ui

import java.time.LocalDate

internal data class TenantChronologyError(val field: String, val message: String)

internal object TenantChronology {
    const val OLD_END = "oldEnd"
    const val NEW_START = "newStart"

    fun changeError(periods: List<TenantPeriod>, currentId: Long?, oldEnd: String, newStart: String): TenantChronologyError? {
        val start = CalendarInput.parseIsoDate(newStart)
            ?: return TenantChronologyError(NEW_START, "Bitte einen gültigen Mietbeginn im Format JJJJ-MM-TT eingeben.")
        val current = periods.firstOrNull { it.id == currentId }
        if (current != null) {
            val end = CalendarInput.parseIsoDate(oldEnd)
                ?: return TenantChronologyError(OLD_END, "Bitte ein gültiges Vertragsende im Format JJJJ-MM-TT eingeben.")
            val oldStart = if (current.startDate.isBlank()) LocalDate.MIN else CalendarInput.parseIsoDate(current.startDate)
                ?: return TenantChronologyError(OLD_END, "Bitte zuerst den bisherigen Mietbeginn korrigieren.")
            if (end.isBefore(oldStart)) return TenantChronologyError(OLD_END, "Das Vertragsende darf nicht vor dem Mietbeginn liegen.")
            if (!start.isAfter(end)) return TenantChronologyError(NEW_START, "Der neue Mietbeginn muss nach dem bisherigen Vertragsende liegen.")
        }
        val remaining = periods.map { if (it.id == currentId) it.copy(endDate = oldEnd.trim()) else it }
        val proposed = TenantPeriod(Long.MIN_VALUE, "", "", newStart.trim(), "", 0.0, 0.0, 0.0)
        return periodsError(remaining + proposed)?.let { TenantChronologyError(NEW_START, it) }
    }

    fun endError(periods: List<TenantPeriod>, currentId: Long?, endDate: String): String? {
        val current = periods.firstOrNull { it.id == currentId } ?: return null
        val end = CalendarInput.parseIsoDate(endDate) ?: return "Bitte ein gültiges Vertragsende eingeben."
        val start = if (current.startDate.isBlank()) LocalDate.MIN else CalendarInput.parseIsoDate(current.startDate)
            ?: return "Bitte zuerst den bisherigen Mietbeginn korrigieren."
        if (end.isBefore(start)) return "Das Vertragsende darf nicht vor dem Mietbeginn liegen."
        return periodsError(periods.map { if (it.id == currentId) it.copy(endDate = endDate) else it })
    }

    fun periodsError(periods: List<TenantPeriod>): String? {
        val intervals = mutableListOf<Pair<LocalDate, LocalDate>>()
        periods.forEach { p ->
            val start = if (p.startDate.isBlank()) LocalDate.MIN else CalendarInput.parseIsoDate(p.startDate)
                ?: return "Ein bestehender Mietbeginn ist ungültig. Bitte den Vertrag zuerst korrigieren."
            val end = if (p.endDate.isBlank()) LocalDate.MAX else CalendarInput.parseIsoDate(p.endDate)
                ?: return "Ein bestehendes Vertragsende ist ungültig. Bitte den Vertrag zuerst korrigieren."
            if (end.isBefore(start)) return "Das Vertragsende darf nicht vor dem Mietbeginn liegen."
            intervals += start to end
        }
        val sorted = intervals.sortedBy { it.first }
        if (sorted.zipWithNext().any { (a, b) -> !b.first.isAfter(a.second) }) {
            return "Der Vertragszeitraum überschneidet sich mit einem bestehenden Mietvertrag."
        }
        return null
    }

    fun status(period: TenantPeriod, date: LocalDate = LocalDate.now()): String = when {
        CalendarInput.parseIsoDate(period.startDate)?.isAfter(date) == true -> "GEPLANT"
        TenantHistoryStore.currentAt(listOf(period), date) != null -> "AKTUELL"
        else -> "BEENDET"
    }
}
