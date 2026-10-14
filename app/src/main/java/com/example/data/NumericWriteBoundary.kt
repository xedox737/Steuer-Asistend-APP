package com.example.data

/** Target-type checks at the persistence boundary; never repairs an existing value. */
internal object NumericWriteBoundary {
    fun float(value: Double): Float {
        require(value.isFinite() && value >= 0.0) { "Bitte eine gültige, nicht negative Zahl eingeben." }
        return value.toFloat().also {
            require(it.isFinite()) { "Der Wert ist zu groß." }
            require(value == 0.0 || it != 0f) { "Der Wert ist zu klein." }
        }
    }

    fun bankRule(rule: BankLearningRule) {
        listOfNotNull(rule.amountMin, rule.amountMax).forEach {
            require(it.isFinite() && it >= 0.0) { "Bitte eine gültige, nicht negative Betragsgrenze eingeben." }
        }
        require(rule.amountMin == null || rule.amountMax == null || rule.amountMin <= rule.amountMax) {
            "Die untere Betragsgrenze darf nicht über der oberen liegen."
        }
    }
}
