from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def patch(path: str, old: str, new: str, label: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


def replace_between(path: str, start: str, end: str, replacement: str, label: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if text.count(start) != 1 or text.count(end) != 1:
        raise RuntimeError(f"{label}: function boundaries are not unique")
    start_idx = text.index(start)
    end_idx = text.index(end, start_idx)
    target.write_text(text[:start_idx] + replacement + "\n\n" + text[end_idx:], encoding="utf-8")


# Kotlin's use{} extension in this test source is defined for Closeable. RoomDatabase
# and SupportSQLiteOpenHelper are closed explicitly in this project.
patch(
    "app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt",
    '''        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build().use { database ->
            assertEquals(24, database.openHelper.writableDatabase.version)
        }
''',
    '''        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            assertEquals(24, database.openHelper.writableDatabase.version)
        } finally {
            database.close()
        }
''',
    "fresh database close"
)
patch(
    "app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt",
    '''        helper.use { block(it.writableDatabase) }
''',
    '''        try {
            block(helper.writableDatabase)
        } finally {
            helper.close()
        }
''',
    "migration helper close"
)

# Strengthen the migration preservation checks: 22->24 explicitly verifies the
# BankAccount row, while 23->24 also carries all pre-existing core sentinels.
patch(
    "app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt",
    '''        assertCoreSentinels(db)
        db.query("SELECT counterparty, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
''',
    '''        assertCoreSentinels(db)
        db.query("SELECT bankName FROM bank_accounts WHERE accountId='a'").use {
            assertTrue(it.moveToFirst()); assertEquals("Sparkasse", it.getString(0))
        }
        db.query("SELECT counterparty, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
''',
    "migration preserves bank account"
)
patch(
    "app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt",
    '''    @Test fun migration23To24IsAdditiveAndPreservesBankRows() = withDb(23) { db ->
        createBank23(db)
''',
    '''    @Test fun migration23To24IsAdditiveAndPreservesBankRows() = withDb(23) { db ->
        createCoreSentinels(db)
        createBank23(db)
''',
    "migration 23 core setup"
)
patch(
    "app/src/test/java/com/example/data/BankMigration24AcceptanceTest.kt",
    '''        MIGRATION_23_24.migrate(db)
        db.query("SELECT propertyId, importFileName, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
''',
    '''        MIGRATION_23_24.migrate(db)
        assertCoreSentinels(db)
        db.query("SELECT propertyId, importFileName, updatedAt FROM bank_transactions WHERE transactionId='t'").use {
''',
    "migration 23 core preservation"
)

# The checklist asks for manual searching through open bank bookings from the
# receipt side. Replace the complete chooser function through unique function
# boundaries, avoiding brittle brace-level edits.
patch(
    "app/src/main/java/com/example/ui/BankFeature.kt",
    '''import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
''',
    '''import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
''',
    "OutlinedTextField import"
)

replace_between(
    "app/src/main/java/com/example/ui/BankFeature.kt",
    "@Composable\nprivate fun BankTransactionPickerDialog(",
    "@Composable\nprivate fun NoReceiptReasonDialog",
    '''@Composable
private fun BankTransactionPickerDialog(
    receipt: Receipt,
    transactions: List<BankTransaction>,
    links: List<BankReceiptLink>,
    onDismiss: () -> Unit,
    onSelect: (BankTransaction) -> Unit
) {
    val ranked = remember(receipt.id, transactions, links) {
        BankReceiptMatcher.rankTransactionsForReceipt(receipt, transactions, links)
    }
    var query by remember(receipt.id) { mutableStateOf("") }
    val visible = remember(ranked, query) {
        val needle = query.trim().lowercase(java.util.Locale.GERMANY)
        if (needle.isBlank()) ranked else ranked.filter { suggestion ->
            transactions.firstOrNull { it.transactionId == suggestion.transactionId }?.let { transaction ->
                listOf(
                    transaction.bookingDate,
                    transaction.counterparty,
                    transaction.purpose,
                    transaction.bankReference,
                    transaction.amount.toString()
                ).any { it.lowercase(java.util.Locale.GERMANY).contains(needle) }
            } == true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Andere Buchung auswählen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Offene Bankbuchungen durchsuchen") }
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                if (query.isBlank()) "Keine offene Bankbuchung ist für diesen Beleg verfügbar."
                                else "Keine Buchung passt zur Suche."
                            )
                        }
                    } else {
                        items(visible, key = { it.transactionId }) { suggestion ->
                            val transaction = transactions.firstOrNull { it.transactionId == suggestion.transactionId }
                            if (transaction != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = BorderStroke(1.dp, BorderColor),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(Modifier.padding(10.dp)) {
                                        Text(
                                            transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${transaction.bookingDate} • ${NumberFormatter.format(transaction.amount)} • Score ${suggestion.score}%",
                                            fontSize = 12.sp
                                        )
                                        if (suggestion.reasons.isNotEmpty()) {
                                            Text(
                                                suggestion.reasons.take(3).joinToString(" • "),
                                                fontSize = 11.sp,
                                                color = SlateGray
                                            )
                                        }
                                        TextButton(onClick = { onSelect(transaction) }) { Text("Zuordnen") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}''',
    "manual bank transaction chooser"
)

print("Bank Phase 1 fix1 applied successfully")
