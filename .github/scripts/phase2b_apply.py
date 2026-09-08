from pathlib import Path

root = Path('.')

def patch(path, old, new):
    p = root / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:120]}')
    p.write_text(text.replace(old, new, 1))

# Room 25 -> 26 with persistent user-confirmed rent assignments.
db='app/src/main/java/com/example/data/ReceiptDatabase.kt'
patch(db,
'''@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class, BankAccount::class, BankTransaction::class, BankReceiptLink::class, BankLearningRule::class, BankRuleEvidence::class], version = 25, exportSchema = false)''',
'''val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("""CREATE TABLE IF NOT EXISTS bank_rent_assignments (assignmentId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, rentMonth TEXT NOT NULL, tenantReference TEXT NOT NULL, allocatedAmount REAL NOT NULL, paymentType TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, receiptId INTEGER, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_rent_assignments_transactionId ON bank_rent_assignments(transactionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_rent_assignments_rent_scope ON bank_rent_assignments(propertyId, unitId, rentMonth)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_rent_assignments_tenantReference ON bank_rent_assignments(tenantReference)")
    }
}

@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class, BankAccount::class, BankTransaction::class, BankReceiptLink::class, BankLearningRule::class, BankRuleEvidence::class, BankRentAssignment::class], version = 26, exportSchema = false)''')
patch(db,
'''    abstract fun bankLearningRuleDao(): BankLearningRuleDao
''',
'''    abstract fun bankLearningRuleDao(): BankLearningRuleDao
    abstract fun bankRentAssignmentDao(): BankRentAssignmentDao
''')
patch(db,
'''MIGRATION_23_24, MIGRATION_24_25)''',
'''MIGRATION_23_24, MIGRATION_24_25, MIGRATION_25_26)''')

# Supplemental backup schema 8 -> 9, additive and backward compatible.
bk='app/src/main/java/com/example/data/SupplementalDriveBackup.kt'
patch(bk, 'internal const val SCHEMA_VERSION = 8', 'internal const val SCHEMA_VERSION = 9')
patch(bk,
'''        put("bankRuleEvidence", JSONArray().apply { database.bankLearningRuleDao().getAllEvidence().forEach { put(it.toBackupJson()) } })
''',
'''        put("bankRuleEvidence", JSONArray().apply { database.bankLearningRuleDao().getAllEvidence().forEach { put(it.toBackupJson()) } })
        put("bankRentAssignments", JSONArray().apply { database.bankRentAssignmentDao().getAll().forEach { put(it.toBackupJson()) } })
''')
patch(bk,
'''            val learningEvidence = root.optJSONArray("bankRuleEvidence") ?: JSONArray()
            for (index in 0 until learningEvidence.length()) database.bankLearningRuleDao().insertEvidence(learningEvidence.getJSONObject(index).toBankRuleEvidence())
''',
'''            val learningEvidence = root.optJSONArray("bankRuleEvidence") ?: JSONArray()
            for (index in 0 until learningEvidence.length()) database.bankLearningRuleDao().insertEvidence(learningEvidence.getJSONObject(index).toBankRuleEvidence())
            val rentAssignments = root.optJSONArray("bankRentAssignments") ?: JSONArray()
            for (index in 0 until rentAssignments.length()) database.bankRentAssignmentDao().upsert(rentAssignments.getJSONObject(index).toBankRentAssignment())
''')
patch(bk,
'''    private fun BankReceiptLink.toBackupJson() = JSONObject().apply {
''',
'''    private fun BankRentAssignment.toBackupJson() = JSONObject().apply {
        put("assignmentId", assignmentId); put("transactionId", transactionId); put("propertyId", propertyId)
        put("unitId", unitId); put("rentMonth", rentMonth); put("tenantReference", tenantReference)
        put("allocatedAmount", allocatedAmount); put("paymentType", paymentType); put("status", status)
        put("source", source); receiptId?.let { put("receiptId", it) }; put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toBankRentAssignment() = BankRentAssignment(
        assignmentId = optString("assignmentId", ""), transactionId = optString("transactionId", ""),
        propertyId = optString("propertyId", ""), unitId = optString("unitId", ""), rentMonth = optString("rentMonth", ""),
        tenantReference = optString("tenantReference", ""), allocatedAmount = optDouble("allocatedAmount", 0.0),
        paymentType = optString("paymentType", "UNKLAR"), status = optString("status", BankRentAssignmentStatus.CONFIRMED),
        source = optString("source", BankRentAssignmentSource.USER_CONFIRMED),
        receiptId = if (has("receiptId") && !isNull("receiptId")) optInt("receiptId") else null,
        createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", "")
    )

    private fun BankReceiptLink.toBackupJson() = JSONObject().apply {
''')

# Keep existing schema acceptance aligned with the additive schema bump.
for test in [
    'app/src/test/java/com/example/data/BankBackup7AcceptanceTest.kt',
    'app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt',
    'app/src/test/java/com/example/data/BankLearningRulesBackupAcceptanceTest.kt'
]:
    p=root/test
    if p.exists():
        t=p.read_text()
        t=t.replace('assertEquals(8, payload.getInt("schemaVersion"))', 'assertEquals(9, payload.getInt("schemaVersion"))')
        t=t.replace('assertEquals(8, SupplementalDriveBackup.SCHEMA_VERSION)', 'assertEquals(9, SupplementalDriveBackup.SCHEMA_VERSION)')
        p.write_text(t)
