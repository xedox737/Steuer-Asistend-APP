from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)

root = Path('.')

# 1) Small product policy/service for REVIEW transitions and UI classification.
review_service = root / 'app/src/main/java/com/example/data/BankReviewStatusService.kt'
if review_service.exists():
    raise SystemExit('BankReviewStatusService.kt already exists; refusing to overwrite')
review_service.write_text('''package com.example.data

import java.time.Instant

/** Minimal final Phase-1 rules for the manual REVIEW state. */
class BankReviewStatusService(
    private val dao: BankDao,
    private val nowProvider: () -> String = { Instant.now().toString() }
) {
    suspend fun markForReview(transactionId: String): String? {
        val transaction = dao.getTransaction(transactionId) ?: return null
        val links = dao.getLinksForTransaction(transactionId)
        if (links.isNotEmpty()) return keepLinkedStatus(transaction, links)
        if (transaction.reconciliationStatus != BankReconciliationStatus.OPEN) {
            return transaction.reconciliationStatus
        }
        dao.updateTransactionStatus(
            transactionId,
            BankReconciliationStatus.REVIEW,
            "",
            nowProvider()
        )
        return BankReconciliationStatus.REVIEW
    }

    suspend fun reopen(transactionId: String): String? {
        val transaction = dao.getTransaction(transactionId) ?: return null
        val links = dao.getLinksForTransaction(transactionId)
        if (links.isNotEmpty()) return keepLinkedStatus(transaction, links)
        if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN) {
            return BankReconciliationStatus.OPEN
        }
        dao.updateTransactionStatus(
            transactionId,
            BankReconciliationStatus.OPEN,
            "",
            nowProvider()
        )
        return BankReconciliationStatus.OPEN
    }

    private suspend fun keepLinkedStatus(
        transaction: BankTransaction,
        links: List<BankReceiptLink>
    ): String {
        val linkedStatus = BankLinkPolicy.statusFor(transaction, links)
        if (linkedStatus != transaction.reconciliationStatus) {
            dao.updateTransactionStatus(
                transaction.transactionId,
                linkedStatus,
                "",
                nowProvider()
            )
        }
        return linkedStatus
    }
}

object BankReviewUiPolicy {
    fun isReviewQueue(status: String): Boolean = status in setOf(
        BankReconciliationStatus.OPEN,
        BankReconciliationStatus.PARTIAL,
        BankReconciliationStatus.REVIEW
    )

    fun isMatched(status: String): Boolean = status == BankReconciliationStatus.MATCHED

    fun isNoReceiptRequired(status: String): Boolean =
        status == BankReconciliationStatus.NO_RECEIPT_REQUIRED

    fun isCompleted(status: String): Boolean = isMatched(status) || isNoReceiptRequired(status)
}
''', encoding='utf-8')

# 2) Patch BankFeature minimally: shared UI classification + REVIEW actions.
bank_feature_path = root / 'app/src/main/java/com/example/ui/BankFeature.kt'
bank_feature = bank_feature_path.read_text(encoding='utf-8')
bank_feature = replace_once(
    bank_feature,
    'import com.example.data.BankReconciliationStatus\nimport com.example.data.BankTransaction',
    'import com.example.data.BankReconciliationStatus\nimport com.example.data.BankReviewUiPolicy\nimport com.example.data.BankTransaction',
    'BankFeature import'
)
old_counts = '''    val reviewStates = setOf(BankReconciliationStatus.OPEN, BankReconciliationStatus.PARTIAL, BankReconciliationStatus.REVIEW)
    val reviewCount = filteredTransactions.count { it.reconciliationStatus in reviewStates }
    val matchedCount = filteredTransactions.count { it.reconciliationStatus == BankReconciliationStatus.MATCHED }
    val missingReceiptCount = filteredTransactions.count { transaction ->
        transaction.amount < 0 && transaction.reconciliationStatus in reviewStates &&
            (transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL || suggestions[transaction.transactionId] == null)
    }
    val noReceiptCount = filteredTransactions.count { it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED }
    val shown = when (filter) {
        BankListFilter.REVIEW -> filteredTransactions.filter { it.reconciliationStatus in reviewStates }
        BankListFilter.MATCHED -> filteredTransactions.filter {
            it.reconciliationStatus == BankReconciliationStatus.MATCHED ||
                it.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED
        }
        BankListFilter.ALL -> filteredTransactions
    }
'''
new_counts = '''    val reviewCount = filteredTransactions.count { BankReviewUiPolicy.isReviewQueue(it.reconciliationStatus) }
    val matchedCount = filteredTransactions.count { BankReviewUiPolicy.isMatched(it.reconciliationStatus) }
    val missingReceiptCount = filteredTransactions.count { transaction ->
        transaction.amount < 0 && BankReviewUiPolicy.isReviewQueue(transaction.reconciliationStatus) &&
            (transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL || suggestions[transaction.transactionId] == null)
    }
    val noReceiptCount = filteredTransactions.count { BankReviewUiPolicy.isNoReceiptRequired(it.reconciliationStatus) }
    val shown = when (filter) {
        BankListFilter.REVIEW -> filteredTransactions.filter { BankReviewUiPolicy.isReviewQueue(it.reconciliationStatus) }
        BankListFilter.MATCHED -> filteredTransactions.filter { BankReviewUiPolicy.isCompleted(it.reconciliationStatus) }
        BankListFilter.ALL -> filteredTransactions
    }
'''
bank_feature = replace_once(bank_feature, old_counts, new_counts, 'BankFeature counters')
bank_feature = replace_once(
    bank_feature,
    '                    onNoReceipt = { noReceiptFor = transaction },\n                    onReopen = { viewModel.reopenBankTransaction(transaction.transactionId) },',
    '                    onNoReceipt = { noReceiptFor = transaction },\n                    onManualReview = { viewModel.markBankTransactionForReview(transaction.transactionId) },\n                    onReopen = { viewModel.reopenBankTransaction(transaction.transactionId) },',
    'BankFeature card invocation'
)
bank_feature = replace_once(
    bank_feature,
    '    onPickReceipt: () -> Unit,\n    onNoReceipt: () -> Unit,\n    onReopen: () -> Unit,',
    '    onPickReceipt: () -> Unit,\n    onNoReceipt: () -> Unit,\n    onManualReview: () -> Unit,\n    onReopen: () -> Unit,',
    'BankFeature card signature'
)
old_actions = '''                TextButton(onClick = onNoReceipt, modifier = Modifier.align(Alignment.End)) {
                    Text("Kein Beleg erforderlich")
                }
'''
new_actions = '''                if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN && linkedLinks.isEmpty()) {
                    OutlinedButton(onClick = onManualReview, modifier = Modifier.fillMaxWidth()) {
                        Text("Manuell prüfen")
                    }
                } else if (transaction.reconciliationStatus == BankReconciliationStatus.REVIEW && linkedLinks.isEmpty()) {
                    OutlinedButton(onClick = onReopen, modifier = Modifier.fillMaxWidth()) {
                        Text("Wieder auf offen setzen")
                    }
                }
                if (transaction.reconciliationStatus != BankReconciliationStatus.REVIEW) {
                    TextButton(onClick = onNoReceipt, modifier = Modifier.align(Alignment.End)) {
                        Text("Kein Beleg erforderlich")
                    }
                }
'''
bank_feature = replace_once(bank_feature, old_actions, new_actions, 'BankFeature review actions')
bank_feature_path.write_text(bank_feature, encoding='utf-8')

# 3) Patch ViewModel only around REVIEW/reopen status actions.
vm_path = root / 'app/src/main/java/com/example/ui/ReceiptViewModel.kt'
vm = vm_path.read_text(encoding='utf-8')
anchor = '    fun reopenBankTransaction(transactionId: String) {'
if vm.count(anchor) != 1:
    raise SystemExit(f'ReceiptViewModel reopen anchor count={vm.count(anchor)}')
mark_method = '''    fun markBankTransactionForReview(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = com.example.data.BankReviewStatusService(database.bankDao())
                .markForReview(transactionId)
            _bankImportStatus.value = when (status) {
                com.example.data.BankReconciliationStatus.REVIEW -> "Buchung wurde zur manuellen Prüfung markiert."
                com.example.data.BankReconciliationStatus.PARTIAL,
                com.example.data.BankReconciliationStatus.MATCHED -> "Buchung bleibt gemäß bestehender Zuordnung verknüpft."
                com.example.data.BankReconciliationStatus.NO_RECEIPT_REQUIRED -> "Buchung zuerst wieder öffnen, bevor sie manuell geprüft wird."
                else -> "Bankstatus blieb unverändert."
            }
        }
    }

'''
vm = vm.replace(anchor, mark_method + anchor, 1)
pattern = re.compile(r'''    fun reopenBankTransaction\(transactionId: String\) \{.*?\n    \}\n\n    fun removeBankReceiptLink''', re.S)
match = pattern.search(vm)
if not match:
    raise SystemExit('ReceiptViewModel reopen method block not found')
replacement = '''    fun reopenBankTransaction(transactionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val status = com.example.data.BankReviewStatusService(database.bankDao())
                .reopen(transactionId)
            _bankImportStatus.value = when (status) {
                com.example.data.BankReconciliationStatus.OPEN -> "Buchung wurde wieder zur Prüfung geöffnet."
                com.example.data.BankReconciliationStatus.PARTIAL,
                com.example.data.BankReconciliationStatus.MATCHED -> "Buchung bleibt gemäß bestehender Zuordnung verknüpft."
                else -> "Bankstatus blieb unverändert."
            }
        }
    }

    fun removeBankReceiptLink'''
vm = pattern.sub(replacement, vm, count=1)
vm_path.write_text(vm, encoding='utf-8')

# 4) Real Room integration tests for REVIEW and duplicate re-import.
test_path = root / 'app/src/test/java/com/example/data/BankPhase1LastPatchAcceptanceTest.kt'
if test_path.exists():
    raise SystemExit('BankPhase1LastPatchAcceptanceTest.kt already exists; refusing to overwrite')
test_path.write_text('''package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPhase1LastPatchAcceptanceTest {
    private lateinit var context: Context
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After fun tearDown() = database.close()

    @Test fun manualReviewOpenToReviewAndBackToOpenUpdatesAuditAndUiClassification() = runTest {
        val dao = database.bankDao()
        dao.upsertTransaction(BankTransaction("review", "a", "2026-09-07", amount = -10.0, importedAt = "imported"))
        var now = "2026-09-07T21:30:00Z"
        val service = BankReviewStatusService(dao) { now }

        assertEquals(BankReconciliationStatus.REVIEW, service.markForReview("review"))
        val reviewed = dao.getTransaction("review")!!
        assertEquals(BankReconciliationStatus.REVIEW, reviewed.reconciliationStatus)
        assertEquals("", reviewed.noReceiptReason)
        assertEquals("2026-09-07T21:30:00Z", reviewed.updatedAt)
        assertTrue(BankReviewUiPolicy.isReviewQueue(reviewed.reconciliationStatus))
        assertFalse(BankReviewUiPolicy.isMatched(reviewed.reconciliationStatus))
        assertFalse(BankReviewUiPolicy.isNoReceiptRequired(reviewed.reconciliationStatus))

        now = "2026-09-07T21:31:00Z"
        assertEquals(BankReconciliationStatus.OPEN, service.reopen("review"))
        val reopened = dao.getTransaction("review")!!
        assertEquals(BankReconciliationStatus.OPEN, reopened.reconciliationStatus)
        assertEquals("2026-09-07T21:31:00Z", reopened.updatedAt)
    }

    @Test fun manualReviewDoesNotDeleteLinksOrOverridePartialAndMatched() = runTest {
        val dao = database.bankDao()
        val partial = BankTransaction("partial", "a", "2026-09-07", amount = -150.0, reconciliationStatus = BankReconciliationStatus.PARTIAL)
        val matched = BankTransaction("matched", "a", "2026-09-07", amount = -150.0, reconciliationStatus = BankReconciliationStatus.MATCHED)
        dao.upsertTransaction(partial)
        dao.upsertTransaction(matched)
        dao.upsertLink(BankReceiptLink("lp", "partial", 1, "r1", 100.0))
        dao.upsertLink(BankReceiptLink("lm", "matched", 2, "r2", 150.0))
        val service = BankReviewStatusService(dao) { "changed" }

        assertEquals(BankReconciliationStatus.PARTIAL, service.markForReview("partial"))
        assertEquals(BankReconciliationStatus.MATCHED, service.markForReview("matched"))
        assertEquals(BankReconciliationStatus.PARTIAL, dao.getTransaction("partial")!!.reconciliationStatus)
        assertEquals(BankReconciliationStatus.MATCHED, dao.getTransaction("matched")!!.reconciliationStatus)
        assertEquals(1, dao.getLinksForTransaction("partial").size)
        assertEquals(1, dao.getLinksForTransaction("matched").size)
    }

    @Test fun noReceiptRequiredMustBeReopenedBeforeManualReview() = runTest {
        val dao = database.bankDao()
        dao.upsertTransaction(
            BankTransaction(
                "no-receipt", "a", "2026-09-07", amount = -7.5,
                reconciliationStatus = BankReconciliationStatus.NO_RECEIPT_REQUIRED,
                noReceiptReason = "Bankgebühr",
                updatedAt = "original"
            )
        )
        val service = BankReviewStatusService(dao) { "changed" }
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, service.markForReview("no-receipt"))
        val unchanged = dao.getTransaction("no-receipt")!!
        assertEquals("Bankgebühr", unchanged.noReceiptReason)
        assertEquals("original", unchanged.updatedAt)

        assertEquals(BankReconciliationStatus.OPEN, service.reopen("no-receipt"))
        assertEquals(BankReconciliationStatus.REVIEW, service.markForReview("no-receipt"))
    }

    @Test fun identicalCsvReimportKeepsExactlyThreeRowsAndPreservesExistingData() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            01.09.2026;Hornbach;Material;-84,50;REF-1
            02.09.2026;Versicherung;Beitrag;-120,00;REF-2
            03.09.2026;Max Mustermann;Miete September;950,00;REF-3
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "konto.csv")
        database.bankDao().upsertAccount(first.account)
        database.bankDao().insertTransactions(first.transactions)
        assertEquals(3, database.bankDao().getAllTransactions().size)
        val firstIds = first.transactions.map { it.transactionId }

        database.bankDao().updateTransactionStatus(firstIds.first(), BankReconciliationStatus.REVIEW, "", "reviewed")
        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "konto.csv")
        database.bankDao().upsertAccount(second.account)
        database.bankDao().insertTransactions(second.transactions)

        val stored = database.bankDao().getAllTransactions()
        assertEquals(3, stored.size)
        assertEquals(firstIds.toSet(), second.transactions.map { it.transactionId }.toSet())
        assertEquals(firstIds.toSet(), stored.map { it.transactionId }.toSet())
        assertEquals(BankReconciliationStatus.REVIEW, database.bankDao().getTransaction(firstIds.first())!!.reconciliationStatus)
        assertEquals("reviewed", database.bankDao().getTransaction(firstIds.first())!!.updatedAt)
    }

    @Test fun identicalDuplicateRowsStayTwoRealStableRowsAcrossRoomReimport() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            04.09.2026;A;X;-10,00;R
            04.09.2026;A;X;-10,00;R
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "dup.csv")
        assertEquals(2, first.transactions.size)
        assertNotEquals(first.transactions[0].transactionId, first.transactions[1].transactionId)
        database.bankDao().insertTransactions(first.transactions)
        assertEquals(2, database.bankDao().getAllTransactions().size)

        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "dup.csv")
        assertEquals(first.transactions.map { it.transactionId }, second.transactions.map { it.transactionId })
        database.bankDao().insertTransactions(second.transactions)
        assertEquals(2, database.bankDao().getAllTransactions().size)
    }

    @Test fun differentReferencesRemainDistinctAcrossRoomReimport() = runTest {
        val csv = """
            Buchungstag;Auftraggeber/Empfänger;Verwendungszweck;Betrag;Referenz
            05.09.2026;A;X;-10,00;REF-A
            05.09.2026;A;X;-10,00;REF-B
        """.trimIndent()
        val first = BankImportParser.parseCsv(csv, "Hauskonto", "first", "refs.csv")
        assertEquals(2, first.transactions.size)
        assertNotEquals(first.transactions[0].transactionId, first.transactions[1].transactionId)
        database.bankDao().insertTransactions(first.transactions)

        val second = BankImportParser.parseCsv(csv, "Hauskonto", "second", "refs.csv")
        database.bankDao().insertTransactions(second.transactions)
        assertEquals(first.transactions.map { it.transactionId }, second.transactions.map { it.transactionId })
        assertEquals(2, database.bankDao().getAllTransactions().size)
    }
}
''', encoding='utf-8')

print('Bank Phase 1 last patch applied successfully')
