package com.example.util

data class DatevFormatValidationResult(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

/**
 * Strict structural validation for DATEV Buchungsstapel format version 13.
 * It intentionally runs after serialization so writer defects cannot be hidden
 * by the semantic booking validation.
 */
object DatevFormatValidator {

    private const val CRLF = "\r\n"

    private data class CsvField(
        val raw: String,
        val value: String,
        val quoted: Boolean
    )

    fun validate(csv: String): DatevFormatValidationResult {
        val errors = mutableListOf<String>()

        if (Regex("(?<!\\r)\\n").containsMatchIn(csv)) {
            errors += "Die DATEV-Datei enthält LF-Zeilenenden ohne CR."
        }
        if (csv.contains('\r') && !csv.contains(CRLF)) {
            errors += "Die DATEV-Datei enthält ungültige CR-Zeilenenden."
        }
        if (!csv.endsWith(CRLF)) {
            errors += "Die letzte DATEV-Zeile endet nicht mit CR/LF."
        }

        val normalized = csv
            .removeSuffix(CRLF)
            .removeSuffix("\n")
            .removeSuffix("\r")
            .replace(CRLF, "\n")
            .replace('\r', '\n')
        val lines = if (normalized.isEmpty()) emptyList() else normalized.split("\n")
        if (lines.size < 3) {
            errors += "Ein Buchungsstapel benötigt Header, Spaltenzeile und mindestens einen Buchungssatz."
            return DatevFormatValidationResult(errors.distinct())
        }

        val parsedLines = lines.mapIndexed { index, line ->
            runCatching { parseLine(line) }
                .onFailure { errors += "Zeile ${index + 1}: ${it.message}" }
                .getOrNull()
        }

        parsedLines.getOrNull(0)?.let { validateHeader(it, errors) }
        parsedLines.getOrNull(1)?.let { validateColumnHeader(it, errors) }
        parsedLines.drop(2).forEachIndexed { index, fields ->
            fields?.let { validateBookingRow(index + 3, it, errors) }
        }

        return DatevFormatValidationResult(errors.distinct())
    }

    private fun validateHeader(fields: List<CsvField>, errors: MutableList<String>) {
        if (fields.size != 31) {
            errors += "DATEV-Header: ${fields.size} statt 31 Felder."
            return
        }

        val expectedQuotes = listOf(
            true, false, false, true, false, false, false, true, true, true,
            false, false, false, false, false, false, true, true, false, false,
            false, true, false, true, false, false, true, false, false, true, true
        )
        expectedQuotes.forEachIndexed { index, expected ->
            if (fields[index].quoted != expected) {
                val kind = if (expected) "Textfeld mit Anführungszeichen" else "Zahlen-/Leerfeld ohne Anführungszeichen"
                errors += "DATEV-Header Feld ${index + 1}: erwartet $kind."
            }
        }

        requireValue(fields, 1, "EXTF", errors)
        requireValue(fields, 2, "700", errors)
        requireValue(fields, 3, "21", errors)
        requireValue(fields, 4, "Buchungsstapel", errors)
        requireValue(fields, 5, "13", errors)
        requireRegex(fields, 6, Regex("20\\d{15}"), "Zeitstempel YYYYMMDDHHMMSSFFF", errors)
        requireRegex(fields, 11, Regex("\\d{4,7}"), "gültige Beraternummer", errors)
        requireRegex(fields, 12, Regex("\\d{1,5}"), "gültige Mandantennummer", errors)
        requireRegex(fields, 13, Regex("20\\d{6}"), "Wirtschaftsjahresbeginn YYYYMMDD", errors)
        requireRegex(fields, 14, Regex("[4-8]"), "Sachkontenlänge 4 bis 8", errors)
        requireRegex(fields, 15, Regex("20\\d{6}"), "Periodenbeginn YYYYMMDD", errors)
        requireRegex(fields, 16, Regex("20\\d{6}"), "Periodenende YYYYMMDD", errors)
        requireRegex(fields, 19, Regex("[12]"), "Buchungstyp 1 oder 2", errors)
        requireRegex(fields, 20, Regex("0|30|40|50|64"), "Rechnungslegungszweck", errors)
        requireRegex(fields, 21, Regex("[01]"), "Festschreibungskennzeichen 0 oder 1", errors)
        requireRegex(fields, 22, Regex("[A-Z]{3}"), "ISO-Währung", errors)
        if (fields[26].value.isNotEmpty() && !fields[26].value.matches(Regex("\\d{2}|\\d{4}"))) {
            errors += "DATEV-Header Feld 27: ungültiger Sachkontenrahmen."
        }
    }

    private fun validateColumnHeader(fields: List<CsvField>, errors: MutableList<String>) {
        if (fields.size != DatevCsvSerializer.EXTF_BOOKING_COLUMN_COUNT) {
            errors += "DATEV-Spaltenzeile: ${fields.size} statt ${DatevCsvSerializer.EXTF_BOOKING_COLUMN_COUNT} Felder."
            return
        }
        fields.forEachIndexed { index, field ->
            val expected = DatevCsvSerializer.EXTF_HEADER_COLUMNS_LIST[index]
            if (field.value != expected || field.quoted) {
                errors += "DATEV-Spaltenzeile Feld ${index + 1}: erwartet '$expected' ohne Anführungszeichen."
            }
        }
    }

    private fun validateBookingRow(lineNumber: Int, fields: List<CsvField>, errors: MutableList<String>) {
        if (fields.size != DatevCsvSerializer.EXTF_BOOKING_COLUMN_COUNT) {
            errors += "Zeile $lineNumber: ${fields.size} statt ${DatevCsvSerializer.EXTF_BOOKING_COLUMN_COUNT} Buchungsfelder."
            return
        }

        requireUnquotedRegex(fields, 1, Regex("(?!0+(?:,00)?$)\\d{1,10},\\d{2}"), "positiver Umsatz mit Dezimalkomma", lineNumber, errors)
        requireQuotedRegex(fields, 2, Regex("S|H"), "Soll-/Haben-Kennzeichen S oder H", lineNumber, errors)
        requireUnquotedRegex(fields, 7, Regex("\\d{1,9}"), "numerisches Konto", lineNumber, errors)
        requireUnquotedRegex(fields, 8, Regex("\\d{1,9}"), "numerisches Gegenkonto", lineNumber, errors)
        requireQuoted(fields, 9, lineNumber, errors)
        requireUnquotedRegex(fields, 10, Regex("(?:0[1-9]|[12]\\d|3[01])(?:0[1-9]|1[0-2])"), "Belegdatum TTMM", lineNumber, errors)
        requireQuotedNonBlank(fields, 11, 36, "Belegfeld 1", lineNumber, errors)
        requireQuoted(fields, 12, lineNumber, errors)
        requireQuotedMax(fields, 14, 60, "Buchungstext", lineNumber, errors)
        requireQuoted(fields, 37, lineNumber, errors)
        requireQuoted(fields, 38, lineNumber, errors)

        for (fieldNumber in 48..87) {
            requireQuoted(fields, fieldNumber, lineNumber, errors)
        }

        requireQuoted(fields, 103, lineNumber, errors)
        val guid = fields[102].value
        if (guid.isNotEmpty() && !guid.matches(Regex("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}"))) {
            errors += "Zeile $lineNumber Feld 103: ungültige Buchungs-GUID."
        }
    }

    private fun requireValue(fields: List<CsvField>, fieldNumber: Int, expected: String, errors: MutableList<String>) {
        if (fields[fieldNumber - 1].value != expected) {
            errors += "DATEV-Header Feld $fieldNumber: erwartet '$expected'."
        }
    }

    private fun requireRegex(fields: List<CsvField>, fieldNumber: Int, regex: Regex, label: String, errors: MutableList<String>) {
        if (!fields[fieldNumber - 1].value.matches(regex)) {
            errors += "DATEV-Header Feld $fieldNumber: erwartet $label."
        }
    }

    private fun requireUnquotedRegex(
        fields: List<CsvField>,
        fieldNumber: Int,
        regex: Regex,
        label: String,
        lineNumber: Int,
        errors: MutableList<String>
    ) {
        val field = fields[fieldNumber - 1]
        if (field.quoted || !field.value.matches(regex)) {
            errors += "Zeile $lineNumber Feld $fieldNumber: erwartet $label ohne Anführungszeichen."
        }
    }

    private fun requireQuotedRegex(
        fields: List<CsvField>,
        fieldNumber: Int,
        regex: Regex,
        label: String,
        lineNumber: Int,
        errors: MutableList<String>
    ) {
        val field = fields[fieldNumber - 1]
        if (!field.quoted || !field.value.matches(regex)) {
            errors += "Zeile $lineNumber Feld $fieldNumber: erwartet $label als Textfeld."
        }
    }

    private fun requireQuoted(fields: List<CsvField>, fieldNumber: Int, lineNumber: Int, errors: MutableList<String>) {
        if (!fields[fieldNumber - 1].quoted) {
            errors += "Zeile $lineNumber Feld $fieldNumber: Textfeld muss in Anführungszeichen stehen."
        }
    }

    private fun requireQuotedNonBlank(
        fields: List<CsvField>,
        fieldNumber: Int,
        maxLength: Int,
        label: String,
        lineNumber: Int,
        errors: MutableList<String>
    ) {
        val field = fields[fieldNumber - 1]
        if (!field.quoted || field.value.isBlank() || field.value.length > maxLength) {
            errors += "Zeile $lineNumber Feld $fieldNumber: $label muss ein Textfeld mit 1 bis $maxLength Zeichen sein."
        }
    }

    private fun requireQuotedMax(
        fields: List<CsvField>,
        fieldNumber: Int,
        maxLength: Int,
        label: String,
        lineNumber: Int,
        errors: MutableList<String>
    ) {
        val field = fields[fieldNumber - 1]
        if (!field.quoted || field.value.length > maxLength) {
            errors += "Zeile $lineNumber Feld $fieldNumber: $label muss ein Textfeld mit höchstens $maxLength Zeichen sein."
        }
    }

    private fun parseLine(line: String): List<CsvField> {
        val result = mutableListOf<CsvField>()
        var index = 0

        while (true) {
            if (index > line.length) break

            if (index == line.length) {
                result += CsvField("", "", false)
                break
            }

            val start = index
            if (line[index] == '"') {
                index++
                val value = StringBuilder()
                var closed = false
                while (index < line.length) {
                    val char = line[index]
                    if (char == '"') {
                        if (index + 1 < line.length && line[index + 1] == '"') {
                            value.append('"')
                            index += 2
                        } else {
                            index++
                            closed = true
                            break
                        }
                    } else {
                        value.append(char)
                        index++
                    }
                }
                require(closed) { "nicht geschlossenes Textfeld" }
                require(index == line.length || line[index] == ';') { "Zeichen nach schließendem Anführungszeichen" }
                result += CsvField(line.substring(start, index), value.toString(), true)
            } else {
                while (index < line.length && line[index] != ';') {
                    require(line[index] != '"') { "Anführungszeichen in unquotiertem Feld" }
                    index++
                }
                result += CsvField(line.substring(start, index), line.substring(start, index), false)
            }

            if (index == line.length) break
            index++
            if (index == line.length) {
                result += CsvField("", "", false)
                break
            }
        }
        return result
    }
}
