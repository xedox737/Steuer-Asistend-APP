package com.example.data

/** Retains legacy account/transaction IDs and all relationships; no data migration is necessary. */
object BankImportAccountResolver {
    fun resolve(batch: BankImportBatch, existing: List<BankAccount>, selected: BankAccount? = null): BankImportBatch {
        val identifier = BankTransactionIdentity.normalizeAccountIdentifier(batch.account.iban)
        val matches = existing.filter {
            identifier.isNotBlank() && BankTransactionIdentity.normalizeAccountIdentifier(it.iban) == identifier
        }
        val target = when {
            identifier.isBlank() -> requireNotNull(selected) { "Die Datei enthält keine eigene Kontokennung. Bitte ein Konto auswählen oder ausdrücklich anlegen." }
            matches.size > 1 -> matches.firstOrNull { it.accountId == selected?.accountId }
                ?: throw IllegalArgumentException("Mehrere Konten besitzen diese Kontokennung. Bitte das Zielkonto auswählen.")
            matches.size == 1 -> matches.single()
            else -> batch.account.copy(displayName = "Bankkonto ${identifier.takeLast(4)}", iban = identifier)
        }
        require(target.accountId.isNotBlank()) { "Bitte ein Konto auswählen." }
        val occurrences = mutableMapOf<String, Int>()
        return batch.copy(
            account = batch.account.copy(
                accountId = target.accountId, displayName = target.displayName,
                iban = target.iban.ifBlank { identifier }, bankName = target.bankName.ifBlank { batch.account.bankName },
                accountHolder = target.accountHolder.ifBlank { batch.account.accountHolder },
                active = target.active, createdAt = target.createdAt.ifBlank { batch.account.createdAt }
            ),
            transactions = batch.transactions.map { tx ->
                val baseId = BankTransactionIdentity.transactionId(target.accountId, tx.bookingDate, tx.valueDate, tx.amount,
                    tx.currency, tx.counterparty, tx.counterpartyIban, tx.purpose, tx.bankReference)
                val occurrence = occurrences.getOrDefault(baseId, 0)
                occurrences[baseId] = occurrence + 1
                tx.copy(accountId = target.accountId, transactionId = BankTransactionIdentity.transactionId(
                    target.accountId, tx.bookingDate, tx.valueDate, tx.amount, tx.currency,
                    tx.counterparty, tx.counterpartyIban, tx.purpose, tx.bankReference, occurrence
                ))
            }
        )
    }
}
