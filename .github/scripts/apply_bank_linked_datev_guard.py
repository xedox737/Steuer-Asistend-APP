from pathlib import Path

path = Path('app/src/main/java/com/example/ui/ReceiptViewModel.kt')
text = path.read_text()

setup_marker = '''        val included = mutableListOf<Receipt>()
        val excluded = mutableListOf<Receipt>()
        val exclusionReasons = linkedMapOf<String, List<String>>()

        allRecs.forEach { receipt ->
'''
setup_new = '''        val included = mutableListOf<Receipt>()
        val excluded = mutableListOf<Receipt>()
        val exclusionReasons = linkedMapOf<String, List<String>>()
        val currentBankLinks = bankReceiptLinks.value
        val currentBankTransactions = bankTransactions.value

        allRecs.forEach { receipt ->
'''
if 'val currentBankLinks = bankReceiptLinks.value' not in text:
    assert setup_marker in text
    text = text.replace(setup_marker, setup_new, 1)

eligibility_marker = '''            reasons += com.example.util.DatevReceiptEligibility.issues(receipt)
                .map { it.message }
'''
eligibility_new = '''            reasons += com.example.util.DatevReceiptEligibility.issues(receipt)
                .map { it.message }
            reasons += com.example.data.BankLinkedReceiptDatevPolicy.exclusions(
                receipt = receipt,
                links = currentBankLinks,
                transactions = currentBankTransactions
            ).map { "${it.code}: ${it.message}" }
'''
if 'BankLinkedReceiptDatevPolicy.exclusions(' not in text:
    assert eligibility_marker in text
    text = text.replace(eligibility_marker, eligibility_new, 1)

legacy_marker = '''        val config = com.example.util.DatevConfig(
'''
legacy_guard = '''        val bankDatevExclusions = receipts.flatMap { receipt ->
            com.example.data.BankLinkedReceiptDatevPolicy.exclusions(
                receipt = receipt,
                links = bankReceiptLinks.value,
                transactions = bankTransactions.value
            ).map { exclusion -> "${exclusion.code}: ${exclusion.message}" }
        }
        if (bankDatevExclusions.isNotEmpty()) {
            Log.w("ReceiptViewModel", "DATEV export blocked by bank classification: ${bankDatevExclusions.joinToString(" | ")}")
            return null
        }

'''
# There is one DatevConfig creation in this legacy method after the property names. Anchor more narrowly.
legacy_context = '''        val propShort = if (metaName.isNotEmpty()) metaName.take(15) else "MFH Sulz"

        val config = com.example.util.DatevConfig(
'''
legacy_replacement = '''        val propShort = if (metaName.isNotEmpty()) metaName.take(15) else "MFH Sulz"

''' + legacy_guard + '''        val config = com.example.util.DatevConfig(
'''
if 'DATEV export blocked by bank classification' not in text:
    assert legacy_context in text
    text = text.replace(legacy_context, legacy_replacement, 1)

path.write_text(text)
print('Productive bank-linked DATEV guard applied')
