package com.example.ui

/** Confirmed document text is still untrusted input. Never strip characters or infer separators. */
internal object DocumentNumberInput {
    private val loanFields = mapOf(
        "darlehensbetrag" to LoanInputValidation.Field.AMOUNT, "restschuld" to LoanInputValidation.Field.BALANCE,
        "sollzins" to LoanInputValidation.Field.INTEREST, "tilgung" to LoanInputValidation.Field.REPAYMENT,
        "monatsrate" to LoanInputValidation.Field.MONTHLY, "vermietungsanteil" to LoanInputValidation.Field.RENTED)
    private val propertyFields = setOf("kaufpreis", "grundstuecksflaeche", "wohnflaeche", "kaltmiete", "nebenkosten", "sonstige")
    fun validate(values: Map<String, String>): Map<String, Double> = buildMap {
        values.forEach { (field, raw) ->
            when {
                field in loanFields -> {
                    val result = LoanInputValidation.parse(raw, loanFields.getValue(field))
                    require(result.error == null) { "$field: ${result.error}" }
                    put(field, requireNotNull(result.value) { "$field: Bitte einen Wert eingeben." })
                }
                field in propertyFields -> {
                    val result = NumberFieldInput.nonNegative(raw, floatTarget = field in setOf("wohnflaeche", "kaltmiete", "nebenkosten", "sonstige"))
                    require(result.error == null) { "$field: ${result.error}" }
                    put(field, requireNotNull(result.value))
                }
                field == "baujahr" -> put(field, requireNotNull(PropertyFormInput.year(raw)) { "Bitte ein gültiges Baujahr eingeben." }.toDouble())
            }
        }
    }
}
