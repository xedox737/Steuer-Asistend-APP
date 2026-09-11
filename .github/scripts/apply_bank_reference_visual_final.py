from pathlib import Path


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 occurrence, got {count}")
    print(f"{label}: ok")
    return text.replace(old, new, 1)


path = Path("app/src/main/java/com/example/ui/BankFeature.kt")
s = path.read_text(encoding="utf-8")
original = s

# Reference artwork: Haisch Baumarkt uses the red cart icon; Hornbach uses the grey tool icon.
old_visual = '''        listOf("baumarkt", "hornbach", "obi", "toom", "bauhaus", "handwerk", "werkzeug").any { it in text } ->
            BankTransactionVisual(Icons.Default.Build, Color(0xFFEFF3F8), Color(0xFF64748B))
        listOf("stadtwerk", "strom", "gas", "wasser", "energie").any { it in text } ->'''
new_visual = '''        listOf("hornbach", "handwerk", "werkzeug").any { it in text } ->
            BankTransactionVisual(Icons.Default.Build, Color(0xFFEFF3F8), Color(0xFF64748B))
        listOf("haisch", "baumarkt", "obi", "toom", "bauhaus", "kartenzahlung").any { it in text } ->
            BankTransactionVisual(Icons.Default.ShoppingCart, Color(0xFFFEE2E2), Color(0xFFEF4444))
        listOf("stadtwerk", "strom", "gas", "wasser", "energie").any { it in text } ->'''
s = once(s, old_visual, new_visual, "transaction reference icons")

# The approved mockup shows a document icon for "Beleg anlegen", not a generic plus.
s = once(
    s,
    'BankQuickAction("Beleg\\nanlegen", Icons.Default.Add, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })',
    'BankQuickAction("Beleg\\nanlegen", Icons.Default.Description, Modifier.weight(1f), onClick = { viewModel.startReceiptFromBankTransaction(transaction) })',
    "receipt create icon",
)

# Match the reference action cards more closely.
s = once(
    s,
    'modifier = modifier.height(78.dp).clickable(onClick = onClick),',
    'modifier = modifier.height(88.dp).clickable(onClick = onClick),',
    "quick action height",
)
s = once(
    s,
    'Icon(icon, contentDescription = label.replace(\'\\n\', \' \'), tint = AccentBlue, modifier = Modifier.size(21.dp))\n            Text(label, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)',
    'Icon(icon, contentDescription = label.replace(\'\\n\', \' \'), tint = if (label.startsWith("Kein Beleg")) EmeraldGreen else AccentBlue, modifier = Modifier.size(25.dp))\n            Spacer(Modifier.height(4.dp))\n            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = DarkNavy, maxLines = 2, overflow = TextOverflow.Ellipsis)',
    "quick action reference styling",
)

# Reference uses a bottom red "Buchung ignorieren" affordance. Preserve existing business semantics:
# it opens the existing reason dialog and marks NO_RECEIPT_REQUIRED; it never deletes a transaction.
anchor = '''        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Kein Beleg erforderlich", fontWeight = FontWeight.Bold, color = DarkNavy)
                        if (transaction.noReceiptReason.isNotBlank()) Text(transaction.noReceiptReason, color = SlateGray)
                        OutlinedButton(onClick = { viewModel.reopenBankTransaction(transaction.transactionId) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Buchung wieder öffnen") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }'''
replacement = '''        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, BorderColor)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Kein Beleg erforderlich", fontWeight = FontWeight.Bold, color = DarkNavy)
                        if (transaction.noReceiptReason.isNotBlank()) Text(transaction.noReceiptReason, color = SlateGray)
                        OutlinedButton(onClick = { viewModel.reopenBankTransaction(transaction.transactionId) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) { Text("Buchung wieder öffnen") }
                    }
                }
            }
        } else {
            item {
                OutlinedButton(
                    onClick = onNoReceipt,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    border = BorderStroke(1.dp, CrimsonRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Buchung ignorieren", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { Spacer(Modifier.height(20.dp)) }'''
s = once(s, anchor, replacement, "reference ignore action")

if s == original:
    raise SystemExit("No changes produced")

path.write_text(s, encoding="utf-8")
print("Final Bank reference visual patch written")
