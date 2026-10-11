package com.example.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.Instant
import java.util.TimeZone

class DateFormatUtilsTest {
    @Test fun strictGermanInputAndLegacyRead() {
        assertEquals("11.10.2026", DateFormatUtils.formatIsoToGerman("2026-10-11"))
        assertEquals("2026-10-11", DateFormatUtils.parseGermanToIso("11.10.2026"))
        assertEquals("2028-02-29", DateFormatUtils.parseGermanToIso("29.02.2028"))
        listOf("29.02.2027", "31.04.2026", "31.02.2026", "32.01.2026", "00.12.2026", "11.13.2026", "11.10.26", "2026-10-11", "32122026", "01.01.0000").forEach {
            assertNull(it, DateFormatUtils.parseGermanToIso(it))
        }
        assertEquals("", DateFormatUtils.safeFormat(""))
        assertNull(DateFormatUtils.safeParse(""))
        assertEquals(LocalDate.of(2026, 10, 11), DateFormatUtils.safeParse("2026-10-11"))
        assertEquals(LocalDate.of(2026, 10, 11), DateFormatUtils.safeParse("11.10.2026"))
        assertEquals("11.10.2026", DateFormatUtils.safeFormat("11.10.2026"))
        assertEquals("ungültig", DateFormatUtils.safeFormat("ungültig"))
        assertEquals("Zeitraum: 11.10.2026 bis 31.12.2026", DateFormatUtils.displayText("Zeitraum: 2026-10-11 bis 2026-12-31"))
    }
    @Test fun pickerCalendarDaysDoNotDependOnDefaultZone() {
        val original = TimeZone.getDefault()
        try {
            listOf("UTC", "Europe/Berlin", "America/Los_Angeles", "Pacific/Kiritimati").forEach { zone ->
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                listOf("2026-01-01", "2026-12-31", "2026-03-29", "2026-10-25").forEach { iso ->
                    val date = LocalDate.parse(iso)
                    val midnightUtc = Instant.parse("${iso}T00:00:00Z").toEpochMilli()
                    assertEquals("$zone $iso: UTC encoding", midnightUtc, DateFormatUtils.toPickerMillis(date))
                    assertEquals("$zone $iso: Material UTC selection", iso, DateFormatUtils.fromPickerMillis(midnightUtc).toString())
                    assertEquals("$zone $iso", date, DateFormatUtils.fromPickerMillis(DateFormatUtils.toPickerMillis(date)))
                }
            }
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun displayFormattingDoesNotChangeChronology() {
        val values = listOf("2026-12-02", "2026-01-11")
        assertEquals(listOf("11.01.2026", "02.12.2026"), values.sorted().map(DateFormatUtils::safeFormat))
        assertNull(DateFormatUtils.parseIso("11.10.2026"))
    }
}
