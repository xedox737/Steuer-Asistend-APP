package com.example.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/** UI boundary only. Storage, ordering, AI and exchange formats remain ISO. */
object DateFormatUtils {
    const val INPUT_ERROR = "Bitte ein gültiges Datum im Format TT.MM.JJJJ eingeben."
    private val isoPattern = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val germanPattern = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
    private val textPattern = Regex("(?<!\\d)\\d{4}-\\d{2}-\\d{2}(?!\\d)")
    private val german = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMANY)
        .withResolverStyle(ResolverStyle.STRICT)

    fun parseIso(value: String): LocalDate? = value.trim().takeIf(isoPattern::matches)?.let {
        runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }?.takeIf { it.year in 1..9999 }

    /** Strict human input: never accepts ISO or two-digit years. */
    fun parseGerman(value: String): LocalDate? = value.trim().takeIf(germanPattern::matches)?.let {
        runCatching { LocalDate.parse(it, german) }.getOrNull()
    }?.takeIf { it.year in 1..9999 }

    /** Tolerant read boundary for existing ISO and German legacy calendar dates. */
    fun safeParse(value: String): LocalDate? = parseIso(value) ?: parseGerman(value)
    fun parseGermanToIso(value: String): String? = parseGerman(value)?.toString()
    fun formatIsoToGerman(value: String): String = safeFormat(value)
    fun format(date: LocalDate): String = date.format(german)
    fun safeFormat(value: String): String = if (value.isBlank()) "" else safeParse(value)?.let(::format) ?: value

    /** Render dates inside user-facing prose/AI summaries without altering the source. */
    fun displayText(value: String): String = textPattern.replace(value) {
        safeFormat(it.value)
    }

    // Material DatePicker encodes calendar days as midnight UTC, independent of device zone.
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
