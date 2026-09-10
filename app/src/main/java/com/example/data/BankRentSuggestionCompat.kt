package com.example.data

/**
 * Compatibility alias for the existing rent-matching suggestion model, which currently lives
 * in the UI package. This keeps the compact bank-details patch source-compatible without
 * introducing a second persisted or duplicated model.
 */
typealias BankRentSuggestion = com.example.ui.BankRentSuggestion
