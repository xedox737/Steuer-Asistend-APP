package com.example.qa

/** Call before opening databases, preferences or files, including disposable E2E setup. */
object Phase11AIsolation {
    const val PRODUCTION = "com.aistudio.steuerassistent.vnhqkp"
    const val EXPECTED = "$PRODUCTION.phase11a"
    const val PRODUCTION_ERROR = "Phase 11A evidence/seed is not allowed on the production applicationId."

    fun requireTestPackage(applicationId: String, debuggable: Boolean) {
        check(applicationId != PRODUCTION) { PRODUCTION_ERROR }
        check(applicationId == EXPECTED && debuggable) { "Phase 11A requires the isolated debug applicationId: $EXPECTED" }
    }
}
