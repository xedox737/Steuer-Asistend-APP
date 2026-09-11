from pathlib import Path

path = Path('app/src/main/java/com/example/ui/ReceiptAppUi.kt')
text = path.read_text()

state_anchor = '''fun ReceiptDetailDialog(receipt: Receipt, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
'''
state_replacement = '''fun ReceiptDetailDialog(receipt: Receipt, viewModel: ReceiptViewModel, onDismiss: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val bankReceiptLinks by viewModel.bankReceiptLinks.collectAsState()
    val bankTransactions by viewModel.bankTransactions.collectAsState()
    val linkedBankEntries = remember(receipt.id, receipt.internalId, bankReceiptLinks, bankTransactions) {
        val transactionById = bankTransactions.associateBy { it.transactionId }
        bankReceiptLinks
            .filter { link ->
                link.receiptId == receipt.id ||
                    (receipt.internalId.isNotBlank() && link.receiptInternalId == receipt.internalId)
            }
            .mapNotNull { link -> transactionById[link.transactionId]?.let { transaction -> link to transaction } }
            .sortedByDescending { (_, transaction) -> transaction.bookingDate }
    }
'''
assert state_anchor in text, 'ReceiptDetailDialog state anchor missing'
text = text.replace(state_anchor, state_replacement, 1)

ui_anchor = '''                    // 2.2 Google Drive Synchronisationsstatus
                    var showDriveDetails by remember { mutableStateOf(false) }
'''
ui_block = '''                    if (linkedBankEntries.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("receipt_bank_links_card"),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.dp, BorderColor)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (linkedBankEntries.size == 1) "Mit 1 Bankbuchung verknüpft" else "Mit ${linkedBankEntries.size} Bankbuchungen verknüpft",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = DarkNavy
                                )
                                linkedBankEntries.forEach { (link, transaction) ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(SoftBackground, RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = transaction.counterparty.ifBlank { transaction.purpose.ifBlank { "Bankbuchung" } },
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 12.sp,
                                                    color = DarkNavy,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = transaction.bookingDate,
                                                    fontSize = 10.sp,
                                                    color = SlateGray
                                                )
                                            }
                                            Text(
                                                text = NumberFormatter.format(transaction.amount),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = DarkNavy
                                            )
                                        }
                                        TextButton(
                                            onClick = { viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) },
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            Text("Verknüpfung lösen", fontSize = 11.sp, color = CrimsonRed)
                                        }
                                    }
                                }
                                Text(
                                    "Der Beleg bleibt gespeichert, wenn nur eine einzelne Bank-Verknüpfung gelöst wird.",
                                    fontSize = 10.sp,
                                    color = SlateGray
                                )
                            }
                        }
                    }

                    // 2.2 Google Drive Synchronisationsstatus
                    var showDriveDetails by remember { mutableStateOf(false) }
'''
assert ui_anchor in text, 'Receipt detail Drive anchor missing'
text = text.replace(ui_anchor, ui_block, 1)

path.write_text(text)
print('Receipt bank multi-link UI patch applied')
