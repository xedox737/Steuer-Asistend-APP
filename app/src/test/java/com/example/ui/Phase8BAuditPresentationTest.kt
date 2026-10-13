package com.example.ui

import com.example.data.ExportAuditRun
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class Phase8BAuditPresentationTest {
    @Test fun allRealStatusCodesUseGermanDisplayWithoutChangingRawAudit() {
        mapOf("SUCCESS" to "Erfolgreich", "CREATED_NOT_READY" to "Erstellt · noch nicht bereit",
            "CANCELLED" to "Abgebrochen", "FAILED" to "Fehlgeschlagen").forEach { (raw, display) ->
            val run = ExportAuditRun("test-$raw", status = raw)
            assertEquals(display, DatevAuditPresentation.statusLabel(run.status))
            assertEquals(raw, run.status)
        }
    }
    @Test fun unknownAndEmptyStatusAreSafeAndNeutral() {
        listOf("", "historical-status", "RUNNING").forEach {
            assertEquals("Status unbekannt", DatevAuditPresentation.statusLabel(it))
        }
    }
    @Test fun previewNeverTruncatesStoredHistoryOrChangesDaoOrdering() {
        listOf(0, 1, 5, 6, 20, 100).forEach { count ->
            val runs = (count downTo 1).map { ExportAuditRun("run-$it", timestamp = it.toLong()) }
            val preview = DatevAuditPresentation.preview(runs)
            assertEquals(minOf(count, 5), preview.size)
            assertEquals(count > 5, DatevAuditPresentation.hasMore(runs))
            assertEquals(runs.take(5), preview); assertEquals(count, runs.size)
            if (count > 5) assertEquals("run-${count - 5}", runs[5].exportlaufId)
        }
    }
    @Test fun exportDateIncludesUnambiguousYearFromExistingTimestamp() {
        val label = DatevAuditPresentation.dateLabel(Instant.parse("2024-06-15T12:00:00Z").toEpochMilli())
        assertTrue(label.startsWith("15.06.2024")); assertTrue(label.contains(" · "))
    }
}
