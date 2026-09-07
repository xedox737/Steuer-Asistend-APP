from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PATCH_DIR = ROOT / ".github/workflows/bank-phase1-patches"

def load(name: str) -> str:
    return (PATCH_DIR / name).read_text(encoding="utf-8")

def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly 1 anchor, found {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print(f"{label}: ok")

# --- Room 21 -> 22 ---
db = ROOT / "app/src/main/java/com/example/data/ReceiptDatabase.kt"
migration = load("migration-21-22.txt").rstrip() + "\n"
replace_once(db, "\n@Database(", "\n" + migration + "\n@Database(", "insert migration 21->22")
replace_once(
    db,
    "@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class], version = 21, exportSchema = false)",
    "@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class, BankAccount::class, BankTransaction::class, BankReceiptLink::class], version = 22, exportSchema = false)",
    "database entities/version"
)
replace_once(
    db,
    "    abstract fun managedDocumentDao(): ManagedDocumentDao\n",
    "    abstract fun managedDocumentDao(): ManagedDocumentDao\n    abstract fun bankDao(): BankDao\n",
    "bank dao accessor"
)
replace_once(
    db,
    ".addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)",
    ".addMigrations(MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21, MIGRATION_21_22)",
    "register migration 21->22"
)

# --- ViewModel: persistent bank data + bidirectional receipt workflow ---
vm = ROOT / "app/src/main/java/com/example/ui/ReceiptViewModel.kt"
replace_once(
    vm,
    "    DOCUMENTS,\n    PROPERTIES,\n    MORE\n",
    "    DOCUMENTS,\n    PROPERTIES,\n    BANK,\n    MORE\n",
    "bank app screen"
)
replace_once(
    vm,
    "    private val _bankStatementResetVersion = MutableStateFlow(0)\n    val bankStatementResetVersion = _bankStatementResetVersion.asStateFlow()\n",
    "    private val _bankStatementResetVersion = MutableStateFlow(0)\n    val bankStatementResetVersion = _bankStatementResetVersion.asStateFlow()\n    private val _bankImportStatus = MutableStateFlow<String?>(null)\n    val bankImportStatus: StateFlow<String?> = _bankImportStatus.asStateFlow()\n    private val _pendingBankTransactionId = MutableStateFlow<String?>(null)\n    val pendingBankTransactionId: StateFlow<String?> = _pendingBankTransactionId.asStateFlow()\n",
    "bank state"
)
replace_once(
    vm,
    "    val deletedReceipts: StateFlow<List<Receipt>> = repository.deletedReceipts\n",
    load("viewmodel-flows.txt") + "    val deletedReceipts: StateFlow<List<Receipt>> = repository.deletedReceipts\n",
    "bank observable flows"
)
replace_once(
    vm,
    "    fun resetScanState() {\n        _scanState.value = ScanUiState.Idle\n    }\n",
    load("viewmodel-functions.txt") + "    fun resetScanState() {\n        _pendingBankTransactionId.value = null\n        _scanState.value = ScanUiState.Idle\n    }\n",
    "bank viewmodel actions"
)
replace_once(
    vm,
    "    fun setScreen(screen: AppScreen) {\n        _currentScreen.value = screen\n        _scanState.value = ScanUiState.Idle\n    }\n",
    "    fun setScreen(screen: AppScreen) {\n        if (screen != AppScreen.ADD_RECEIPT) {\n            _pendingBankTransactionId.value = null\n        }\n        _currentScreen.value = screen\n        _scanState.value = ScanUiState.Idle\n    }\n",
    "pending bank receipt navigation"
)
replace_once(
    vm,
    "            val newId = repository.insert(newReceipt)\n            val savedReceipt = newReceipt.copy(id = newId.toInt())\n\n            if (FirestoreService.isCloudActive()) {\n",
    "            val newId = repository.insert(newReceipt)\n            val savedReceipt = newReceipt.copy(id = newId.toInt())\n\n            _pendingBankTransactionId.value?.let { pendingTransactionId ->\n                database.bankDao().getTransaction(pendingTransactionId)?.let { transaction ->\n                    _bankImportStatus.value = confirmBankReceiptLinkInternal(transaction, savedReceipt)\n                }\n                _pendingBankTransactionId.value = null\n            }\n\n            if (FirestoreService.isCloudActive()) {\n",
    "link new receipt to originating bank transaction"
)
replace_once(
    vm,
    "            repository.clearAllData()\n\n            val editor = learnedRulesPrefs.edit()\n",
    "            repository.clearAllData()\n            database.bankDao().clearLinks()\n            database.bankDao().reopenLinkedTransactions()\n\n            val editor = learnedRulesPrefs.edit()\n",
    "receipt reset clears bank links"
)

# --- UI navigation ---
ui = ROOT / "app/src/main/java/com/example/ui/ReceiptAppUi.kt"
replace_once(
    ui,
    "        AppScreen.DOCUMENTS -> \"Dokumentenakte\"\n        AppScreen.PROPERTIES -> \"Immobilien\"\n        AppScreen.MORE -> \"Mehr\"\n",
    "        AppScreen.DOCUMENTS -> \"Dokumentenakte\"\n        AppScreen.PROPERTIES -> \"Immobilien\"\n        AppScreen.BANK -> \"Bank & Belege\"\n        AppScreen.MORE -> \"Mehr\"\n",
    "bank screen title"
)
replace_once(
    ui,
    "                AppScreen.DOCUMENTS -> DocumentManagementScreen(viewModel)\n                AppScreen.PROPERTIES -> ImmobilienManagerScreen(viewModel)\n                AppScreen.MORE -> MoreScreen(viewModel)\n",
    "                AppScreen.DOCUMENTS -> DocumentManagementScreen(viewModel)\n                AppScreen.PROPERTIES -> ImmobilienManagerScreen(viewModel)\n                AppScreen.BANK -> BankScreen(viewModel)\n                AppScreen.MORE -> MoreScreen(viewModel)\n",
    "bank screen routing"
)
replace_once(
    ui,
    "                    IconButton(\n                        onClick = { showAccountSettingsDialog = true },\n                        modifier = Modifier.testTag(\"account_settings_button\")\n                    ) {\n",
    "                    IconButton(\n                        onClick = { viewModel.setScreen(AppScreen.BANK) },\n                        modifier = Modifier.testTag(\"bank_navigation_button\")\n                    ) {\n                        Icon(Icons.Default.AccountBalance, contentDescription = \"Bank & Belege\", tint = DarkNavy)\n                    }\n                    IconButton(\n                        onClick = { showAccountSettingsDialog = true },\n                        modifier = Modifier.testTag(\"account_settings_button\")\n                    ) {\n",
    "bank top navigation"
)
replace_once(
    ui,
    "                        val isSelected = currentScreen == screen ||\n                            (screen == AppScreen.DASHBOARD && currentScreen in setOf(\n                                AppScreen.RENT_OVERVIEW,\n                                AppScreen.TAX_CALCULATOR\n                            ))\n",
    "                        val isSelected = currentScreen == screen ||\n                            (screen == AppScreen.DASHBOARD && currentScreen in setOf(\n                                AppScreen.RENT_OVERVIEW,\n                                AppScreen.TAX_CALCULATOR\n                            )) ||\n                            (screen == AppScreen.MORE && currentScreen == AppScreen.BANK)\n",
    "bank bottom navigation context"
)

# --- Supplemental backup schema 5 ---
backup = ROOT / "app/src/main/java/com/example/data/SupplementalDriveBackup.kt"
replace_once(backup, "    internal const val SCHEMA_VERSION = 4\n", "    internal const val SCHEMA_VERSION = 5\n", "backup schema 5")
replace_once(
    backup,
    "        put(\"managedDocuments\", JSONArray().apply { database.managedDocumentDao().getAll().forEach { put(it.toBackupJson()) } })\n        put(\"rentPlanPrefs\", prefsToJson(context, \"rent_plan_prefs\"))\n",
    "        put(\"managedDocuments\", JSONArray().apply { database.managedDocumentDao().getAll().forEach { put(it.toBackupJson()) } })\n        put(\"bankAccounts\", JSONArray().apply { database.bankDao().getAllAccounts().forEach { put(it.toBackupJson()) } })\n        put(\"bankTransactions\", JSONArray().apply { database.bankDao().getAllTransactions().forEach { put(it.toBackupJson()) } })\n        put(\"bankReceiptLinks\", JSONArray().apply { database.bankDao().getAllLinks().forEach { put(it.toBackupJson()) } })\n        put(\"rentPlanPrefs\", prefsToJson(context, \"rent_plan_prefs\"))\n",
    "backup bank arrays"
)
replace_once(
    backup,
    "            val documents = root.optJSONArray(\"managedDocuments\") ?: JSONArray()\n            for (index in 0 until documents.length()) database.managedDocumentDao().upsert(documents.getJSONObject(index).toManagedDocument())\n            database.managedDocumentDao().clearSearchIndex()\n",
    "            val documents = root.optJSONArray(\"managedDocuments\") ?: JSONArray()\n            for (index in 0 until documents.length()) database.managedDocumentDao().upsert(documents.getJSONObject(index).toManagedDocument())\n            val bankAccounts = root.optJSONArray(\"bankAccounts\") ?: JSONArray()\n            for (index in 0 until bankAccounts.length()) database.bankDao().upsertAccount(bankAccounts.getJSONObject(index).toBankAccount())\n            val bankTransactions = root.optJSONArray(\"bankTransactions\") ?: JSONArray()\n            for (index in 0 until bankTransactions.length()) database.bankDao().upsertTransaction(bankTransactions.getJSONObject(index).toBankTransaction())\n            val bankLinks = root.optJSONArray(\"bankReceiptLinks\") ?: JSONArray()\n            for (index in 0 until bankLinks.length()) {\n                val restored = bankLinks.getJSONObject(index).toBankReceiptLink()\n                val resolvedReceiptId = restored.receiptInternalId.takeIf { it.isNotBlank() }\n                    ?.let { database.receiptDao().getReceiptByInternalId(it)?.id }\n                    ?: restored.receiptId\n                database.bankDao().upsertLink(restored.copy(receiptId = resolvedReceiptId))\n            }\n            database.managedDocumentDao().clearSearchIndex()\n",
    "restore bank arrays"
)
replace_once(
    backup,
    "    private fun Loan.toJson() = JSONObject().apply {\n",
    load("backup-helpers.txt") + "    private fun Loan.toJson() = JSONObject().apply {\n",
    "backup bank json helpers"
)

# --- Regression tests for supplemental backup ---
backup_test = ROOT / "app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt"
replace_once(backup_test, '        assertEquals(4, payload.getInt("schemaVersion"))\n', '        assertEquals(5, payload.getInt("schemaVersion"))\n', "backup schema test")
bank_backup_test = '''    @Test fun bankDataAndConfirmedLinksRestoreIdempotently() = runTest {\n        val receiptId = database.receiptDao().insertReceipt(\n            Receipt(\n                aussteller = "Hornbach", datum = "2026-09-04", uhrzeit = "", bruttobetrag = 247.38,\n                hauptkategorie = "Renovierung", unterkategorie = "Material", kontoNr = "4800",\n                beschreibung = "Material", internalId = "receipt-bank-test"\n            )\n        ).toInt()\n        val account = BankAccount("bank-1", "Hauskonto", iban = "DE123")\n        val transaction = BankTransaction("tx-1", "bank-1", "2026-09-04", amount = -247.38)\n        val link = BankReceiptLink("link-1", "tx-1", receiptId, "receipt-bank-test", 247.38)\n        database.bankDao().upsertAccount(account)\n        database.bankDao().upsertTransaction(transaction.copy(reconciliationStatus = BankReconciliationStatus.MATCHED))\n        database.bankDao().upsertLink(link)\n\n        val payload = SupplementalDriveBackup.createPayload(context, database)\n        database.bankDao().clearLinks()\n        database.bankDao().upsertTransaction(transaction)\n        SupplementalDriveBackup.restorePayload(context, database, payload)\n        SupplementalDriveBackup.restorePayload(context, database, payload)\n\n        assertEquals("DE123", database.bankDao().getAllAccounts().single { it.accountId == "bank-1" }.iban)\n        assertEquals(BankReconciliationStatus.MATCHED, database.bankDao().getTransaction("tx-1")?.reconciliationStatus)\n        assertEquals(247.38, database.bankDao().getAllLinks().single { it.linkId == "link-1" }.allocatedAmount, 0.001)\n    }\n\n'''
replace_once(
    backup_test,
    "    @Test fun schemaOneWithoutLogbookArraysStillRestores() = runTest {\n",
    bank_backup_test + "    @Test fun schemaOneWithoutLogbookArraysStillRestores() = runTest {\n",
    "bank backup regression test"
)

# Guardrails before Gradle sees the result.
required = {
    "app/src/main/java/com/example/data/ReceiptDatabase.kt": ["version = 22", "MIGRATION_21_22", "abstract fun bankDao(): BankDao"],
    "app/src/main/java/com/example/ui/ReceiptViewModel.kt": ["    BANK,", "fun importBankFile", "fun startReceiptFromBankTransaction"],
    "app/src/main/java/com/example/ui/ReceiptAppUi.kt": ["AppScreen.BANK -> BankScreen(viewModel)", 'contentDescription = "Bank & Belege"'],
    "app/src/main/java/com/example/data/SupplementalDriveBackup.kt": ["SCHEMA_VERSION = 5", '"bankTransactions"', '"bankReceiptLinks"'],
}
for relative, needles in required.items():
    text = (ROOT / relative).read_text(encoding="utf-8")
    for needle in needles:
        if needle not in text:
            raise SystemExit(f"guardrail missing {needle} in {relative}")

print("BANK_PHASE1_PATCH_OK")
