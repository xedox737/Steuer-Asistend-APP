from pathlib import Path

backup_path = Path('app/src/main/java/com/example/data/SupplementalDriveBackup.kt')
test_path = Path('app/src/test/java/com/example/data/SupplementalDriveBackupTest.kt')
reimport_test_path = Path('app/src/test/java/com/example/data/BankCamtV8ReimportStatusAcceptanceTest.kt')
backup = backup_path.read_text()
test = test_path.read_text()
reimport_test = reimport_test_path.read_text()

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

assert 'assertEquals(11, payload.getInt("schemaVersion"))' in test
test = test.replace('assertEquals(11, payload.getInt("schemaVersion"))', 'assertEquals(12, payload.getInt("schemaVersion"))', 1)

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

reimport_marker = '''    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
'''
reimport_insert = '''    @Test fun reimportPreservesClassificationReviewAndTransferLink() = runTest {
        val dao = database.bankDao()
        val batch = BankImportParser.parseCamtV8(synthetic052(), "SYNTHETIC", "first")
        dao.upsertAccount(batch.account)
        val tx = batch.transactions.single()
        dao.insertTransactions(listOf(tx))
        dao.updateTransactionClassification(
            transactionId = tx.transactionId,
            classification = BankTransactionClassification.TRANSFER,
            transferCounterAccountId = "own-account-2",
            linkedTransferTransactionId = "counterpart-tx",
            reviewState = BankReviewState.DONE,
            updatedAt = "confirmed"
        )

        dao.insertTransactions(BankImportParser.parseCamtV8(synthetic052(), "SYNTHETIC", "second").transactions)
        val restored = dao.getTransaction(tx.transactionId)!!

        assertEquals(BankTransactionClassification.TRANSFER, restored.classification)
        assertEquals("own-account-2", restored.transferCounterAccountId)
        assertEquals("counterpart-tx", restored.linkedTransferTransactionId)
        assertEquals(BankReviewState.DONE, restored.reviewState)
    }

'''
if 'reimportPreservesClassificationReviewAndTransferLink' not in reimport_test:
    assert reimport_marker in reimport_test
    reimport_test = reimport_test.replace(reimport_marker, reimport_insert + reimport_marker, 1)

backup_path.write_text(backup)
test_path.write_text(test)
reimport_test_path.write_text(reimport_test)
print('Bank classification backup and reimport patch applied')
