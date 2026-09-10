from pathlib import Path


def once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'{label}: expected 1 occurrence, got {count}')
    print(f'{label}: ok')
    return text.replace(old, new, 1)


bank_path = Path('app/src/main/java/com/example/ui/BankFeature.kt')
s = bank_path.read_text(encoding='utf-8')
original_bank = s

s = once(
    s,
    'import androidx.compose.foundation.verticalScroll\n',
    'import androidx.compose.foundation.verticalScroll\nimport androidx.compose.foundation.layout.statusBarsPadding\n',
    'status bar padding import',
)
s = once(
    s,
    'import androidx.compose.material.icons.filled.Description\n',
    'import androidx.compose.material.icons.filled.Description\nimport androidx.compose.material.icons.filled.CalendarMonth\n',
    'calendar icon import',
)
s = once(
    s,
    'import androidx.compose.runtime.Composable\n',
    'import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n',
    'LaunchedEffect import',
)
s = once(
    s,
    'fun BankScreen(viewModel: ReceiptViewModel) {',
    'fun BankScreen(viewModel: ReceiptViewModel, onDetailVisibilityChanged: (Boolean) -> Unit = {}) {',
    'BankScreen detail callback',
)
s = once(
    s,
    '    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }\n\n    BackHandler(enabled = selectedTransaction != null) { selectedTransactionId = null }',
    '    val selectedTransaction = selectedTransactionId?.let { id -> transactions.firstOrNull { it.transactionId == id } }\n\n    LaunchedEffect(selectedTransaction != null) {\n        onDetailVisibilityChanged(selectedTransaction != null)\n    }\n\n    BackHandler(enabled = selectedTransaction != null) { selectedTransactionId = null }',
    'publish detail visibility',
)

# The approved reference shows no import log between summary and transactions.
collapsed = '''                if (!importStatus.isNullOrBlank()) {
                    val headline = importStatus.orEmpty().lineSequence().firstOrNull().orEmpty()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 3.dp).clickable { showImportDetails = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("✓", color = EmeraldGreen, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(5.dp))
                        Text(
                            headline,
                            modifier = Modifier.weight(1f),
                            fontSize = 10.sp,
                            color = SlateGray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("Importdetails", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
'''
s = once(s, collapsed, '', 'remove import status from overview')

menu_anchor = '''                            DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })
                            DropdownMenuItem(text = { Text("Kontoauszug importieren") }, onClick = {'''
menu_new = '''                            DropdownMenuItem(text = { Text("Beleg → Bank-Zuordnung") }, onClick = { toolsMode = BankToolsMode.REVERSE_RECEIPT; toolsMenuOpen = false })
                            if (!importStatus.isNullOrBlank()) {
                                DropdownMenuItem(text = { Text("Importdetails") }, onClick = { showImportDetails = true; toolsMenuOpen = false })
                            }
                            DropdownMenuItem(text = { Text("Kontoauszug importieren") }, onClick = {'''
s = once(s, menu_anchor, menu_new, 'move import details into menu')

s = once(
    s,
    'LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {',
    'LazyColumn(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {',
    'detail safe status bar',
)

s = once(s, 'BankDetailLine("Buchungsdatum", formatDate(transaction.bookingDate))', 'BankDetailLine("Buchungsdatum", formatDate(transaction.bookingDate), Icons.Default.CalendarMonth)', 'booking date detail icon')
s = once(s, 'if (transaction.valueDate.isNotBlank()) BankDetailLine("Valutadatum", formatDate(transaction.valueDate))', 'if (transaction.valueDate.isNotBlank()) BankDetailLine("Valutadatum", formatDate(transaction.valueDate), Icons.Default.CalendarMonth)', 'value date detail icon')
s = once(s, 'if (transaction.purpose.isNotBlank()) BankDetailLine("Verwendungszweck", transaction.purpose)', 'if (transaction.purpose.isNotBlank()) BankDetailLine("Verwendungszweck", transaction.purpose, Icons.Default.Description)', 'purpose detail icon')
s = once(s, 'if (transaction.bankReference.isNotBlank()) BankDetailLine("Referenz", transaction.bankReference)', 'if (transaction.bankReference.isNotBlank()) BankDetailLine("Referenz", transaction.bankReference, Icons.Default.Description)', 'reference detail icon')

account_old = '''                    BankDetailLine("Konto", buildString {
                        append(account?.displayName?.ifBlank { "Bankkonto" } ?: "Bankkonto")
                        account?.iban?.takeIf { it.isNotBlank() }?.let { append("\\n").append(maskedIban(it)) }
                    })'''
account_new = '''                    BankDetailLine("Konto", buildString {
                        append(account?.displayName?.ifBlank { "Bankkonto" } ?: "Bankkonto")
                        account?.iban?.takeIf { it.isNotBlank() }?.let { append("\\n").append(maskedIban(it)) }
                    }, Icons.Default.AccountBalance)'''
s = once(s, account_old, account_new, 'account detail icon')

old_detail_fn = '''@Composable
private fun BankDetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, modifier = Modifier.weight(0.38f), fontSize = 11.sp, color = SlateGray)
        Text(value, modifier = Modifier.weight(0.62f), fontSize = 12.sp, color = DarkNavy)
    }
}'''
new_detail_fn = '''@Composable
private fun BankDetailLine(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(10.dp))
        Text(label, modifier = Modifier.weight(0.34f), fontSize = 11.sp, color = SlateGray)
        Text(value, modifier = Modifier.weight(0.66f), fontSize = 12.sp, color = DarkNavy)
    }
}'''
s = once(s, old_detail_fn, new_detail_fn, 'detail rows match reference')

bank_path.write_text(s, encoding='utf-8')

ui_path = Path('app/src/main/java/com/example/ui/ReceiptAppUi.kt')
u = ui_path.read_text(encoding='utf-8')
original_ui = u

u = once(
    u,
    '    var showAccountSettingsDialog by remember { mutableStateOf(false) }\n    var showKiPowerCenterDialog by remember { mutableStateOf(false) }',
    '    var showAccountSettingsDialog by remember { mutableStateOf(false) }\n    var showKiPowerCenterDialog by remember { mutableStateOf(false) }\n    var bankDetailsOpen by remember { mutableStateOf(false) }',
    'global Bank detail visibility state',
)

# Hide the normal Bank & Belege global app bar while the dedicated detail header is visible.
top_old = '''        topBar = {
            TopAppBar('''
top_new = '''        topBar = {
            if (!(currentScreen == AppScreen.BANK && bankDetailsOpen)) {
            TopAppBar('''
u = once(u, top_old, top_new, 'hide global header on Bank detail')

close_old = '''                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
        },
        bottomBar = {'''
close_new = '''                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    scrolledContainerColor = Color.White
                )
            )
            }
        },
        bottomBar = {'''
u = once(u, close_old, close_new, 'close conditional top bar')

u = once(
    u,
    '                AppScreen.BANK -> BankScreen(viewModel)',
    '                AppScreen.BANK -> BankScreen(viewModel, onDetailVisibilityChanged = { bankDetailsOpen = it })',
    'wire Bank detail header state',
)

ui_path.write_text(u, encoding='utf-8')

if s == original_bank or u == original_ui:
    raise SystemExit('Expected both BankFeature.kt and ReceiptAppUi.kt to change')

print('Bank detail chrome reference patch written')
