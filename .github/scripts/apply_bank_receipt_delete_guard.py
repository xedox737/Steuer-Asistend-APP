from pathlib import Path

# One-shot verified patch: keep receipt deletion safe while bank links exist.
p = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
s = p.read_text()

old = '''        viewModelScope.launch(Dispatchers.IO) {
            val receipt = repository.getReceiptById(id) ?: return@launch
            val email = _googleAccountEmail.value
'''
new = '''        viewModelScope.launch(Dispatchers.IO) {
            val receipt = repository.getReceiptById(id) ?: return@launch
            val deleteDecision = com.example.data.BankReceiptDeletionPolicy.decide(
                receiptId = receipt.id.toLong(),
                links = bankReceiptLinks.value
            )
            if (!deleteDecision.allowed) {
                Log.w("ReceiptViewModel", deleteDecision.reason ?: "Beleg ist noch mit Bankbuchungen verknüpft.")
                return@launch
            }
            val email = _googleAccountEmail.value
'''
if old not in s:
    raise SystemExit('deleteReceipt anchor not found')
s = s.replace(old, new, 1)

old2 = '''    fun permanentlyDeleteReceipt(receipt: Receipt, onComplete: (com.example.data.PermanentDeleteResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val email = _googleAccountEmail.value
'''
new2 = '''    fun permanentlyDeleteReceipt(receipt: Receipt, onComplete: (com.example.data.PermanentDeleteResult) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val deleteDecision = com.example.data.BankReceiptDeletionPolicy.decide(
                receiptId = receipt.id.toLong(),
                links = bankReceiptLinks.value
            )
            if (!deleteDecision.allowed) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    onComplete(com.example.data.PermanentDeleteResult.Error(
                        deleteDecision.reason ?: "Beleg ist noch mit Bankbuchungen verknüpft."
                    ))
                }
                return@launch
            }
            val email = _googleAccountEmail.value
'''
if old2 not in s:
    raise SystemExit('permanentlyDeleteReceipt anchor not found')
s = s.replace(old2, new2, 1)
p.write_text(s)
