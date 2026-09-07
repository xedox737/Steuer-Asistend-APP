from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def patch(path: str, old: str, new: str, label: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one anchor, found {count}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")

# Kotlin's use{} extension in this test source is defined for Closeable. RoomDatabase
# and SupportSQLiteOpenHelper must therefore be closed explicitly in this project.
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

# The acceptance checklist explicitly asks to manually search open bank bookings
# from the receipt side. The first patch already provides the open-booking chooser;
# add a real text search field rather than relying on scrolling only.
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
patch(
    "app/src/main/java/com/example/ui/BankFeature.kt",
    '''    val ranked = remember(receipt.id, transactions, links) {
        BankReceiptMatcher.rankTransactionsForReceipt(receipt, transactions, links)
    }
    AlertDialog(
''',
    '''    val ranked = remember(receipt.id, transactions, links) {
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
''',
    "manual bank transaction search state"
)
patch(
    "app/src/main/java/com/example/ui/BankFeature.kt",
    '''        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 430.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ranked.isEmpty()) {
                    item { Text("Keine offene Bankbuchung ist für diesen Beleg verfügbar.") }
                } else {
                    items(ranked, key = { it.transactionId }) { suggestion ->
''',
    '''        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Offene Bankbuchungen durchsuchen") }
                )
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (visible.isEmpty()) {
                        item { Text(if (query.isBlank()) "Keine offene Bankbuchung ist für diesen Beleg verfügbar." else "Keine Buchung passt zur Suche.") }
                    } else {
                        items(visible, key = { it.transactionId }) { suggestion ->
''',
    "manual bank transaction search UI"
)
patch(
    "app/src/main/java/com/example/ui/BankFeature.kt",
    '''                    }
                }
            }
        },
        confirmButton = {},
''',
    '''                        }
                    }
                }
            }
        },
        confirmButton = {},
''',
    "manual bank transaction search braces"
)

print("Bank Phase 1 fix1 applied successfully")
