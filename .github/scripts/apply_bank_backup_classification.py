from pathlib import Path

backup_path = Path('app/src/main/java/com/example/data/SupplementalDriveBackup.kt')
test_path = Path('app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt')
backup = backup_path.read_text()
test = test_path.read_text()

backup = backup.replace('internal const val SCHEMA_VERSION = 11', 'internal const val SCHEMA_VERSION = 12')

old_write = '''        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)
        put("importedAt", importedAt); put("updatedAt", updatedAt)
'''
new_write = '''        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)
        put("classification", classification); put("transferCounterAccountId", transferCounterAccountId)
        put("linkedTransferTransactionId", linkedTransferTransactionId); put("reviewState", reviewState)
        put("importedAt", importedAt); put("updatedAt", updatedAt)
'''
assert old_write in backup
backup = backup.replace(old_write, new_write, 1)

old_read = '''        reconciliationStatus = optString("reconciliationStatus", BankReconciliationStatus.OPEN),
        noReceiptReason = optString("noReceiptReason", ""), importedAt = optString("importedAt", ""),
        updatedAt = optString("updatedAt", "")
'''
new_read = '''        reconciliationStatus = optString("reconciliationStatus", BankReconciliationStatus.OPEN),
        noReceiptReason = optString("noReceiptReason", ""),
        classification = BankTransactionClassification.normalize(optString("classification", BankTransactionClassification.NORMAL)),
        transferCounterAccountId = optString("transferCounterAccountId", ""),
        linkedTransferTransactionId = optString("linkedTransferTransactionId", ""),
        reviewState = optString("reviewState", BankReviewState.OPEN).takeIf { it in setOf(BankReviewState.OPEN, BankReviewState.DONE) } ?: BankReviewState.OPEN,
        importedAt = optString("importedAt", ""), updatedAt = optString("updatedAt", "")
'''
assert old_read in backup
backup = backup.replace(old_read, new_read, 1)

# Schema assertion
assert 'assertEquals(11, payload.getInt("schemaVersion"))' in test
test = test.replace('assertEquals(11, payload.getInt("schemaVersion"))', 'assertEquals(12, payload.getInt("schemaVersion"))', 1)

# Strengthen bank restore fixture with the new persistent fields.
old_tx = '''        database.bankDao().upsertTransaction(transaction.copy(reconciliationStatus = BankReconciliationStatus.MATCHED))
'''
new_tx = '''        database.bankDao().upsertTransaction(transaction.copy(
            reconciliationStatus = BankReconciliationStatus.MATCHED,
            classification = BankTransactionClassification.TRANSFER,
            transferCounterAccountId = "bank-2",
            linkedTransferTransactionId = "tx-counterpart",
            reviewState = BankReviewState.DONE
        ))
'''
assert old_tx in test
test = test.replace(old_tx, new_tx, 1)

old_assert = '''        assertEquals("import-1", restoredTransaction?.importRunId)
        assertEquals(247.38, restoredLink.allocatedAmount, 0.001)
'''
new_assert = '''        assertEquals("import-1", restoredTransaction?.importRunId)
        assertEquals(BankTransactionClassification.TRANSFER, restoredTransaction?.classification)
        assertEquals("bank-2", restoredTransaction?.transferCounterAccountId)
        assertEquals("tx-counterpart", restoredTransaction?.linkedTransferTransactionId)
        assertEquals(BankReviewState.DONE, restoredTransaction?.reviewState)
        assertEquals(247.38, restoredLink.allocatedAmount, 0.001)
'''
assert old_assert in test
test = test.replace(old_assert, new_assert, 1)

# Old backups must use safe defaults for fields that did not exist yet.
old_compat = '''        assertEquals("", transaction?.propertyId)
        assertEquals("", transaction?.importRunId)
'''
new_compat = '''        assertEquals("", transaction?.propertyId)
        assertEquals("", transaction?.importRunId)
        assertEquals(BankTransactionClassification.NORMAL, transaction?.classification)
        assertEquals("", transaction?.transferCounterAccountId)
        assertEquals("", transaction?.linkedTransferTransactionId)
        assertEquals(BankReviewState.OPEN, transaction?.reviewState)
'''
assert old_compat in test
test = test.replace(old_compat, new_compat, 1)

backup_path.write_text(backup)
test_path.write_text(test)
print('Bank classification backup patch applied')
