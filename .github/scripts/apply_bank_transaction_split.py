from pathlib import Path


def replace_once(path, old, new):
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f'pattern not found in {path}: {old[:120]!r}')
    if text.count(old) != 1:
        raise SystemExit(f'pattern not unique in {path}: {old[:120]!r} count={text.count(old)}')
    p.write_text(text.replace(old, new, 1))

# Existing assignment SSOT: persist the optional manual note without a parallel table.
replace_once(
    'app/src/main/java/com/example/data/BankRentAssignments.kt',
    '    val createdAt: String,\n    val updatedAt: String\n)',
    '    val createdAt: String,\n    val updatedAt: String,\n    val note: String = ""\n)'
)
replace_once(
    'app/src/main/java/com/example/data/BankRentAssignments.kt',
    'object BankRentAssignmentIdentity {\n    fun id(transactionId: String, propertyId: String, unitId: String, rentMonth: String, paymentType: String): String =\n        "rent-${BankTransactionIdentity.sha256(listOf(transactionId, propertyId, unitId, rentMonth, paymentType).joinToString("|")).take(28)}"\n}',
    '''object BankRentAssignmentIdentity {
    fun id(transactionId: String, propertyId: String, unitId: String, rentMonth: String, paymentType: String): String =
        "rent-${BankTransactionIdentity.sha256(listOf(transactionId, propertyId, unitId, rentMonth, paymentType).joinToString("|")).take(28)}"

    /** Stable identity for an explicitly chosen manual split target. Legacy rent ids stay unchanged. */
    fun manualId(
        transactionId: String,
        propertyId: String,
        unitId: String,
        rentMonth: String,
        paymentType: String,
        tenantReference: String
    ): String = "split-${BankTransactionIdentity.sha256(listOf(transactionId, propertyId, unitId, rentMonth, paymentType, tenantReference).joinToString("|")).take(28)}"
}'''
)

# Minimal schema extension required solely for the optional note.
replace_once(
    'app/src/main/java/com/example/data/ReceiptDatabase.kt',
    '@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class, BankAccount::class, BankTransaction::class, BankReceiptLink::class, BankLearningRule::class, BankRuleEvidence::class, BankRentAssignment::class, BankLoanAssignment::class, BankRecurringPattern::class], version = 27, exportSchema = false)',
    '''val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bank_rent_assignments ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

@Database(entities = [Receipt::class, PropertyMetadata::class, Loan::class, ReceiptEntity::class, Beleg::class, ExportAuditRun::class, ReceiptDocumentReference::class, LogbookTrip::class, StandardRoute::class, ManagedDocument::class, DocumentSearchFts::class, DocumentMigrationJournal::class, BankAccount::class, BankTransaction::class, BankReceiptLink::class, BankLearningRule::class, BankRuleEvidence::class, BankRentAssignment::class, BankLoanAssignment::class, BankRecurringPattern::class], version = 28, exportSchema = false)'''
)
replace_once(
    'app/src/main/java/com/example/data/ReceiptDatabase.kt',
    'MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27)',
    'MIGRATION_24_25, MIGRATION_25_26, MIGRATION_26_27, MIGRATION_27_28)'
)

# Backup schema follows the persisted note; old backups restore note as blank.
replace_once('app/src/main/java/com/example/data/SupplementalDriveBackup.kt', 'internal const val SCHEMA_VERSION = 10', 'internal const val SCHEMA_VERSION = 11')
replace_once(
    'app/src/main/java/com/example/data/SupplementalDriveBackup.kt',
    'put("source", source); receiptId?.let { put("receiptId", it) }; put("createdAt", createdAt); put("updatedAt", updatedAt)',
    'put("source", source); receiptId?.let { put("receiptId", it) }; put("createdAt", createdAt); put("updatedAt", updatedAt); put("note", note)'
)
replace_once(
    'app/src/main/java/com/example/data/SupplementalDriveBackup.kt',
    'updatedAt = optString("updatedAt", "")\n    )',
    'updatedAt = optString("updatedAt", ""), note = optString("note", "")\n    )'
)

# ViewModel keeps its database private; expose only the existing Phase-2D service to the split UI.
replace_once(
    'app/src/main/java/com/example/ui/ReceiptViewModel.kt',
    '    private val database = AppDatabase.getDatabase(application, viewModelScope)\n',
    '    private val database = AppDatabase.getDatabase(application, viewModelScope)\n    internal fun bankPhase2DServiceForUi(): com.example.data.BankPhase2DService = com.example.data.BankPhase2DService(database)\n'
)
replace_once(
    'app/src/main/java/com/example/ui/ReceiptViewModel.kt',
    '        val status = com.example.data.BankLinkPolicy.statusFor(transaction, dao.getLinksForTransaction(transactionId))\n        dao.updateTransactionStatus(transactionId, status, "", java.time.Instant.now().toString())',
    '        val status = com.example.data.BankTransactionSplitPolicy.statusFor(transaction, dao.getLinksForTransaction(transactionId), database.bankRentAssignmentDao().getForTransaction(transactionId))\n        dao.updateTransactionStatus(transactionId, status, "", java.time.Instant.now().toString())'
)

# Every transaction card gets the split action/summary without changing the existing card contract.
replace_once(
    'app/src/main/java/com/example/ui/BankFeature.kt',
    '                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }\n                )',
    '                    onUnlink = { link -> viewModel.removeBankReceiptLink(link.linkId, transaction.transactionId) }\n                )\n                BankTransactionSplitActions(viewModel = viewModel, transaction = transaction)'
)

# Phase 2D service: combined receipt + manual assignment remainder/status, atomic split confirmation, edit, unlink.
replace_once(
    'app/src/main/java/com/example/data/BankPhase2DService.kt',
    '    suspend fun executeSafeBatch(',
    '''    suspend fun confirmManualSplit(
        positions: List<BankManualSplitPosition>,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Aufteilung wurde noch nicht final bestätigt.")
        if (positions.isEmpty()) return BankCombinationApplyResult(false, message = "Mindestens eine Teilposition ist erforderlich.")
        val transactionIds = positions.map { it.transactionId }.distinct()
        if (transactionIds.size != 1) return BankCombinationApplyResult(false, message = "Alle Teilpositionen müssen zu derselben Bankbuchung gehören.")
        return database.withTransaction {
            val bankDao = database.bankDao()
            val rentDao = database.bankRentAssignmentDao()
            val transaction = bankDao.getTransaction(transactionIds.single())
                ?: return@withTransaction BankCombinationApplyResult(false, message = "Bankbuchung nicht gefunden.")
            if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) {
                return@withTransaction BankCombinationApplyResult(false, message = "NO_RECEIPT_REQUIRED darf nicht still überschrieben werden.")
            }
            val currentLinks = bankDao.getAllLinks().toMutableList()
            val currentAssignments = rentDao.getForTransaction(transaction.transactionId).toMutableList()
            val plannedLinks = mutableListOf<BankReceiptLink>()
            val plannedAssignments = mutableListOf<BankRentAssignment>()
            val targetKeys = mutableSetOf<String>()
            val appliedIds = mutableListOf<String>()

            for (raw in positions) {
                val validated = BankTransactionSplitPolicy.validatePosition(transaction, raw)
                if (!validated.allowed) return@withTransaction BankCombinationApplyResult(false, message = validated.reason)
                val position = raw.copy(amount = validated.amount, rentMonth = raw.rentMonth.ifBlank { transaction.bookingDate.take(7) })
                if (position.paymentType == BankSplitPaymentType.RECEIPT) {
                    val receiptId = position.receiptId ?: return@withTransaction BankCombinationApplyResult(false, message = "Für 'Beleg zuordnen' muss ein Beleg gewählt werden.")
                    val receipt = database.receiptDao().getReceiptById(receiptId)
                        ?: return@withTransaction BankCombinationApplyResult(false, message = "Gewählter Beleg wurde nicht gefunden.")
                    if (transaction.propertyId.isNotBlank() && receipt.propertyId.isNotBlank() && transaction.propertyId != receipt.propertyId) {
                        return@withTransaction BankCombinationApplyResult(false, message = "Immobilienkonflikt verhindert die Belegzuordnung.")
                    }
                    if (position.propertyId.isNotBlank() && receipt.propertyId.isNotBlank() && position.propertyId != receipt.propertyId) {
                        return@withTransaction BankCombinationApplyResult(false, message = "Immobilienkonflikt zwischen Teilposition und Beleg.")
                    }
                    val linkId = BankLinkPolicy.linkId(transaction.transactionId, receipt.id, receipt.internalId)
                    if (!targetKeys.add("link:$linkId")) return@withTransaction BankCombinationApplyResult(false, message = "Dasselbe Ziel darf in einer Aufteilung nicht doppelt vorkommen.")
                    val existing = currentLinks.firstOrNull { it.linkId == linkId }
                    if (existing != null) {
                        if (kotlin.math.abs(existing.allocatedAmount - position.amount) <= BankAllocationPolicy.MONEY_TOLERANCE) {
                            appliedIds += linkId
                            continue
                        }
                        return@withTransaction BankCombinationApplyResult(false, message = "Bestehende Belegzuordnung hat einen anderen Betrag und wird nicht überschrieben.")
                    }
                    val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, position.amount, currentLinks + plannedLinks)
                    if (!guard.allowed) return@withTransaction BankCombinationApplyResult(false, message = guard.reason)
                    plannedLinks += BankReceiptLink(
                        linkId = linkId, transactionId = transaction.transactionId, receiptId = receipt.id,
                        receiptInternalId = receipt.internalId, allocatedAmount = guard.normalizedAmount,
                        status = BankLinkStatus.CONFIRMED, source = BankLinkSource.NUTZER_BESTAETIGT, createdAt = now()
                    )
                    appliedIds += linkId
                } else {
                    val assignmentId = BankRentAssignmentIdentity.manualId(
                        transaction.transactionId, position.propertyId, position.unitId, position.rentMonth,
                        position.paymentType, position.tenantReference
                    )
                    if (!targetKeys.add("assignment:$assignmentId")) return@withTransaction BankCombinationApplyResult(false, message = "Dasselbe fachliche Ziel darf in einer Aufteilung nicht doppelt vorkommen.")
                    val existing = currentAssignments.firstOrNull { it.assignmentId == assignmentId }
                    if (existing != null) {
                        val sameAmount = kotlin.math.abs(existing.allocatedAmount - position.amount) <= BankAllocationPolicy.MONEY_TOLERANCE
                        if (sameAmount && existing.note == position.note) {
                            appliedIds += assignmentId
                            continue
                        }
                        return@withTransaction BankCombinationApplyResult(false, message = "Bestehende Nutzerzuordnung hat andere Daten und wird nicht still überschrieben.")
                    }
                    plannedAssignments += BankRentAssignment(
                        assignmentId = assignmentId,
                        transactionId = transaction.transactionId,
                        propertyId = position.propertyId,
                        unitId = position.unitId,
                        rentMonth = position.rentMonth,
                        tenantReference = position.tenantReference,
                        allocatedAmount = position.amount,
                        paymentType = position.paymentType,
                        status = BankRentAssignmentStatus.CONFIRMED,
                        source = BankRentAssignmentSource.MANUAL,
                        receiptId = null,
                        createdAt = now(),
                        updatedAt = now(),
                        note = position.note
                    )
                    appliedIds += assignmentId
                }
            }

            val currentAllocated = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, currentLinks, currentAssignments)
            val plannedAmount = BankAllocationPolicy.roundMoney(plannedLinks.sumOf { it.allocatedAmount } + plannedAssignments.sumOf { it.allocatedAmount })
            if (currentAllocated + plannedAmount > transaction.absoluteAmount + BankAllocationPolicy.MONEY_TOLERANCE) {
                return@withTransaction BankCombinationApplyResult(false, message = "Aufteilung überschreitet den verfügbaren Restbetrag der Bankbuchung.")
            }
            plannedLinks.forEach { bankDao.upsertLink(it) }
            plannedAssignments.forEach { rentDao.upsert(it) }
            refreshStatus(bankDao, transaction.transactionId)
            BankCombinationApplyResult(
                true,
                appliedIds.distinct(),
                if (plannedLinks.isEmpty() && plannedAssignments.isEmpty()) "Bereits bestätigt; keine Doppelzuordnung erzeugt." else "Aufteilung wurde vollständig gespeichert.",
                listOf(transaction.transactionId)
            )
        }
    }

    suspend fun changeManualSplitAssignment(
        assignmentId: String,
        newAmount: Double,
        explicitlyConfirmed: Boolean
    ): BankCombinationApplyResult {
        if (!explicitlyConfirmed) return BankCombinationApplyResult(false, message = "Explizite Nutzerbestätigung fehlt.")
        return database.withTransaction {
            val bankDao = database.bankDao()
            val rentDao = database.bankRentAssignmentDao()
            val assignment = rentDao.getById(assignmentId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Teilzuordnung nicht gefunden.")
            val transaction = bankDao.getTransaction(assignment.transactionId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Bankbuchung nicht gefunden.")
            if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) return@withTransaction BankCombinationApplyResult(false, message = "NO_RECEIPT_REQUIRED darf nicht überschrieben werden.")
            if (!newAmount.isFinite() || newAmount <= 0.0) return@withTransaction BankCombinationApplyResult(false, message = "Teilbetrag muss positiv und endlich sein.")
            val normalized = BankAllocationPolicy.roundMoney(newAmount)
            if (normalized <= BankAllocationPolicy.MONEY_TOLERANCE) return@withTransaction BankCombinationApplyResult(false, message = "Teilbetrag muss größer als 0,00 € sein.")
            val links = bankDao.getLinksForTransaction(transaction.transactionId)
            val others = rentDao.getForTransaction(transaction.transactionId).filterNot { it.assignmentId == assignmentId }
            val usedWithoutCurrent = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, links, others)
            if (usedWithoutCurrent + normalized > transaction.absoluteAmount + BankAllocationPolicy.MONEY_TOLERANCE) {
                return@withTransaction BankCombinationApplyResult(false, message = "Geänderter Teilbetrag würde die Bankbuchung überzuordnen.")
            }
            rentDao.upsert(assignment.copy(allocatedAmount = normalized, updatedAt = now()))
            refreshStatus(bankDao, transaction.transactionId)
            BankCombinationApplyResult(true, listOf(assignmentId), "Teilbetrag geändert; Restbetrag und Status neu berechnet.", listOf(transaction.transactionId))
        }
    }

    suspend fun unlinkManualSplitAssignment(assignmentId: String): BankCombinationApplyResult = database.withTransaction {
        val bankDao = database.bankDao()
        val rentDao = database.bankRentAssignmentDao()
        val assignment = rentDao.getById(assignmentId) ?: return@withTransaction BankCombinationApplyResult(false, message = "Teilzuordnung nicht gefunden.")
        rentDao.deleteById(assignmentId)
        refreshStatus(bankDao, assignment.transactionId)
        BankCombinationApplyResult(true, message = "Teilzuordnung gelöst; Restbetrag und Status neu berechnet.", affectedTransactionIds = listOf(assignment.transactionId))
    }

    suspend fun executeSafeBatch('''
)
replace_once(
    'app/src/main/java/com/example/data/BankPhase2DService.kt',
    '            val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, allocation.amount, allLinks + planned)\n            if (!guard.allowed) return@withTransaction BankCombinationApplyResult(false, message = guard.reason)',
    '''            val manualAssignments = database.bankRentAssignmentDao().getForTransaction(transaction.transactionId)
            val combinedRemaining = BankTransactionSplitPolicy.remainingAmount(transaction, allLinks + planned, manualAssignments)
            val normalizedRequested = BankAllocationPolicy.roundMoney(allocation.amount)
            if (normalizedRequested > combinedRemaining + BankAllocationPolicy.MONEY_TOLERANCE) {
                return@withTransaction BankCombinationApplyResult(false, message = "Allocation überschreitet den Restbetrag der Bankbuchung einschließlich manueller Teilzuordnungen.")
            }
            val guard = BankAllocationPolicy.guardAllocation(transaction, receipt, allocation.amount, allLinks + planned)
            if (!guard.allowed) return@withTransaction BankCombinationApplyResult(false, message = guard.reason)'''
)
replace_once(
    'app/src/main/java/com/example/data/BankPhase2DService.kt',
    '        val links = bankDao.getLinksForTransaction(transactionId)\n        val status = BankLinkPolicy.statusFor(transaction, links)\n        bankDao.updateTransactionStatus(transactionId, status, "", now())',
    '        val links = bankDao.getLinksForTransaction(transactionId)\n        val assignments = database.bankRentAssignmentDao().getForTransaction(transactionId)\n        val status = BankTransactionSplitPolicy.statusFor(transaction, links, assignments)\n        bankDao.updateTransactionStatus(transactionId, status, "", now())'
)

Path('app/src/main/java/com/example/data/BankTransactionSplit.kt').write_text(r'''package com.example.data

object BankSplitPaymentType {
    const val RENT = "MIETE"
    const val UTILITIES_PREPAYMENT = "NEBENKOSTENVORAUSZAHLUNG"
    const val DEPOSIT = "KAUTION"
    const val UTILITIES_SETTLEMENT = "NEBENKOSTENNACHZAHLUNG"
    const val OTHER_INCOME = "SONSTIGE_EINNAHME"
    const val RECEIPT = "BELEG"
    const val OTHER_EXPENSE = "SONSTIGE_AUSGABE"

    val all = listOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT, OTHER_INCOME, RECEIPT, OTHER_EXPENSE)
    private val incomeTypes = setOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT, OTHER_INCOME)
    val rentScoped = setOf(RENT, UTILITIES_PREPAYMENT, DEPOSIT, UTILITIES_SETTLEMENT)

    fun label(type: String): String = when (type) {
        RENT -> "Miete"
        UTILITIES_PREPAYMENT -> "Nebenkostenvorauszahlung"
        DEPOSIT -> "Kaution"
        UTILITIES_SETTLEMENT -> "Betriebskosten-/Nebenkostennachzahlung"
        OTHER_INCOME -> "Sonstige Einnahme"
        RECEIPT -> "Beleg zuordnen"
        OTHER_EXPENSE -> "Sonstige Ausgabe"
        else -> type
    }

    fun directionAllowed(type: String, transaction: BankTransaction): Boolean = when (type) {
        RECEIPT -> true
        OTHER_EXPENSE -> !transaction.isIncome
        in incomeTypes -> transaction.isIncome
        else -> false
    }
}

data class BankManualSplitPosition(
    val transactionId: String,
    val amount: Double,
    val paymentType: String,
    val propertyId: String = "",
    val unitId: String = "",
    val tenantReference: String = "",
    val rentMonth: String = "",
    val note: String = "",
    val receiptId: Int? = null
)

data class BankSplitPositionValidation(val allowed: Boolean, val amount: Double = 0.0, val reason: String = "")

data class BankTransactionSplitPreview(
    val originalAmount: Double,
    val alreadyAllocated: Double,
    val newAllocations: Double,
    val remainingAmount: Double,
    val statusAfter: String,
    val valid: Boolean,
    val message: String = ""
)

/** Adapter over the existing receipt-link and rent/payment-assignment SSOT; it does not create a parallel ledger. */
object BankTransactionSplitPolicy {
    fun allocatedAmount(transactionId: String, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): Double =
        BankAllocationPolicy.roundMoney(
            links.asSequence().filter { it.transactionId == transactionId && it.status == BankLinkStatus.CONFIRMED }.sumOf { it.allocatedAmount } +
                assignments.asSequence().filter { it.transactionId == transactionId && it.status == BankRentAssignmentStatus.CONFIRMED }.sumOf { it.allocatedAmount }
        )

    fun remainingAmount(transaction: BankTransaction, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): Double =
        BankAllocationPolicy.roundMoney((transaction.absoluteAmount - allocatedAmount(transaction.transactionId, links, assignments)).coerceAtLeast(0.0))

    fun statusFor(transaction: BankTransaction, links: List<BankReceiptLink>, assignments: List<BankRentAssignment>): String {
        if (transaction.reconciliationStatus == BankReconciliationStatus.NO_RECEIPT_REQUIRED) return BankReconciliationStatus.NO_RECEIPT_REQUIRED
        val allocated = allocatedAmount(transaction.transactionId, links, assignments)
        return when {
            allocated <= BankAllocationPolicy.MONEY_TOLERANCE -> BankReconciliationStatus.OPEN
            transaction.absoluteAmount - allocated <= BankAllocationPolicy.MONEY_TOLERANCE -> BankReconciliationStatus.MATCHED
            else -> BankReconciliationStatus.PARTIAL
        }
    }

    fun validatePosition(transaction: BankTransaction, position: BankManualSplitPosition): BankSplitPositionValidation {
        if (position.transactionId != transaction.transactionId) return BankSplitPositionValidation(false, reason = "Teilposition gehört nicht zur Bankbuchung.")
        if (position.paymentType !in BankSplitPaymentType.all) return BankSplitPositionValidation(false, reason = "Unbekannte Aufteilungskategorie.")
        if (!position.amount.isFinite() || position.amount <= 0.0) return BankSplitPositionValidation(false, reason = "Teilbetrag muss positiv und endlich sein.")
        val amount = BankAllocationPolicy.roundMoney(position.amount)
        if (amount <= BankAllocationPolicy.MONEY_TOLERANCE) return BankSplitPositionValidation(false, reason = "Teilbetrag muss größer als 0,00 € sein.")
        if (!BankSplitPaymentType.directionAllowed(position.paymentType, transaction)) return BankSplitPositionValidation(false, reason = "Kategorie passt nicht zur Zahlungsrichtung der Bankbuchung.")
        if (transaction.propertyId.isNotBlank() && position.propertyId.isNotBlank() && transaction.propertyId != position.propertyId) return BankSplitPositionValidation(false, reason = "propertyId-Konflikt verhindert die Aufteilung.")
        if (position.paymentType in BankSplitPaymentType.rentScoped && (position.propertyId.isBlank() || position.unitId.isBlank())) return BankSplitPositionValidation(false, reason = "Für Miete, Nebenkosten oder Kaution sind Immobilie und Wohneinheit erforderlich.")
        if (position.paymentType == BankSplitPaymentType.RECEIPT && position.receiptId == null) return BankSplitPositionValidation(false, reason = "Für die Belegzuordnung muss ein Beleg gewählt werden.")
        return BankSplitPositionValidation(true, amount)
    }

    fun preview(
        transaction: BankTransaction,
        links: List<BankReceiptLink>,
        assignments: List<BankRentAssignment>,
        positions: List<BankManualSplitPosition>
    ): BankTransactionSplitPreview {
        val original = BankAllocationPolicy.roundMoney(transaction.absoluteAmount)
        val already = allocatedAmount(transaction.transactionId, links, assignments)
        if (positions.isEmpty()) return BankTransactionSplitPreview(original, already, 0.0, remainingAmount(transaction, links, assignments), transaction.reconciliationStatus, false, "Mindestens eine Position ist erforderlich.")
        val validations = positions.map { validatePosition(transaction, it) }
        val invalid = validations.firstOrNull { !it.allowed }
        if (invalid != null) return BankTransactionSplitPreview(original, already, 0.0, remainingAmount(transaction, links, assignments), transaction.reconciliationStatus, false, invalid.reason)
        val added = BankAllocationPolicy.roundMoney(validations.sumOf { it.amount })
        val available = BankAllocationPolicy.roundMoney((original - already).coerceAtLeast(0.0))
        if (added > available + BankAllocationPolicy.MONEY_TOLERANCE) return BankTransactionSplitPreview(original, already, added, BankAllocationPolicy.roundMoney(available - added), transaction.reconciliationStatus, false, "Neue Zuordnungen überschreiten den verfügbaren Restbetrag.")
        val remaining = BankAllocationPolicy.roundMoney((available - added).coerceAtLeast(0.0))
        val status = if (remaining <= BankAllocationPolicy.MONEY_TOLERANCE) BankReconciliationStatus.MATCHED else BankReconciliationStatus.PARTIAL
        return BankTransactionSplitPreview(original, already, added, remaining, status, true)
    }
}
''')

Path('app/src/main/java/com/example/ui/BankTransactionSplitFeature.kt').write_text(r'''package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.BankAllocationPolicy
import com.example.data.BankManualSplitPosition
import com.example.data.BankReconciliationStatus
import com.example.data.BankRentAssignment
import com.example.data.BankSplitPaymentType
import com.example.data.BankTransaction
import com.example.data.BankTransactionSplitPolicy
import com.example.data.Receipt
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.launch

private data class BankSplitDraft(
    val amount: String = "",
    val paymentType: String = BankSplitPaymentType.RENT,
    val propertyId: String = "",
    val unitId: String = "",
    val tenant: String = "",
    val note: String = "",
    val receiptId: Int? = null
)

@Composable
fun BankTransactionSplitActions(viewModel: ReceiptViewModel, transaction: BankTransaction) {
    val links by viewModel.bankReceiptLinks.collectAsState()
    val assignments by viewModel.bankRentAssignments.collectAsState()
    val receipts by viewModel.receipts.collectAsState()
    val units by viewModel.wohneinheitenStatus.collectAsState()
    val property by viewModel.propertyMetadata.collectAsState()
    val transactionAssignments = assignments.filter { it.transactionId == transaction.transactionId }
    var showDialog by remember(transaction.transactionId) { mutableStateOf(false) }
    var editing by remember { mutableStateOf<BankRentAssignment?>(null) }
    var editAmount by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val euro = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    if (transaction.reconciliationStatus == BankReconciliationStatus.OPEN || transaction.reconciliationStatus == BankReconciliationStatus.PARTIAL) {
        OutlinedButton(onClick = { showDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Buchung aufteilen") }
    }

    if (transactionAssignments.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, BorderColor)
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Bestehende Aufteilung", fontWeight = FontWeight.SemiBold, color = DarkNavy)
                transactionAssignments.forEach { assignment ->
                    Text("${euro.format(assignment.allocatedAmount)} → ${BankSplitPaymentType.label(assignment.paymentType)}", color = DarkNavy)
                    if (assignment.propertyId.isNotBlank()) Text("Immobilie: ${assignment.propertyId}${assignment.unitId.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}", color = SlateGray)
                    if (assignment.tenantReference.isNotBlank()) Text("Mieter/Mietverhältnis: ${assignment.tenantReference}", color = SlateGray)
                    if (assignment.note.isNotBlank()) Text("Notiz: ${assignment.note}", color = SlateGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { editing = assignment; editAmount = assignment.allocatedAmount.toString().replace('.', ',') }) { Text("Teilbetrag ändern") }
                        TextButton(onClick = { scope.launch { viewModel.bankPhase2DServiceForUi().unlinkManualSplitAssignment(assignment.assignmentId) } }) { Text("Zuordnung lösen") }
                    }
                }
                val txLinks = links.filter { it.transactionId == transaction.transactionId }
                val allocated = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, txLinks, transactionAssignments)
                Text("Gesamt zugeordnet: ${euro.format(allocated)} • Rest: ${euro.format((transaction.absoluteAmount - allocated).coerceAtLeast(0.0))}", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDialog) {
        BankTransactionSplitDialog(
            transaction = transaction,
            links = links.filter { it.transactionId == transaction.transactionId },
            assignments = transactionAssignments,
            receipts = receipts,
            units = units,
            defaultPropertyId = transaction.propertyId.ifBlank { property?.propertyId.orEmpty() },
            onDismiss = { showDialog = false },
            onConfirm = { positions ->
                scope.launch {
                    val result = viewModel.bankPhase2DServiceForUi().confirmManualSplit(positions, true)
                    if (result.success) showDialog = false
                }
            }
        )
    }

    editing?.let { assignment ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Teilbetrag ändern") },
            text = { OutlinedTextField(value = editAmount, onValueChange = { editAmount = it }, label = { Text("Betrag") }) },
            confirmButton = {
                Button(onClick = {
                    val amount = editAmount.replace(',', '.').toDoubleOrNull()
                    if (amount != null) scope.launch {
                        val result = viewModel.bankPhase2DServiceForUi().changeManualSplitAssignment(assignment.assignmentId, amount, true)
                        if (result.success) editing = null
                    }
                }) { Text("Änderung bestätigen") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Abbrechen") } }
        )
    }
}

@Composable
private fun BankTransactionSplitDialog(
    transaction: BankTransaction,
    links: List<com.example.data.BankReceiptLink>,
    assignments: List<BankRentAssignment>,
    receipts: List<Receipt>,
    units: List<WohneinheitStatus>,
    defaultPropertyId: String,
    onDismiss: () -> Unit,
    onConfirm: (List<BankManualSplitPosition>) -> Unit
) {
    var drafts by remember(transaction.transactionId) {
        mutableStateOf(listOf(BankSplitDraft(propertyId = defaultPropertyId, unitId = transaction.unitId, tenant = if (transaction.isIncome) transaction.counterparty else "")))
    }
    var previewMode by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf<Int?>(null) }
    var unitMenu by remember { mutableStateOf<Int?>(null) }
    var receiptMenu by remember { mutableStateOf<Int?>(null) }
    val euro = remember { NumberFormat.getCurrencyInstance(Locale.GERMANY) }

    fun positions(): List<BankManualSplitPosition> = drafts.map { d ->
        BankManualSplitPosition(
            transactionId = transaction.transactionId,
            amount = d.amount.replace(',', '.').toDoubleOrNull() ?: Double.NaN,
            paymentType = d.paymentType,
            propertyId = d.propertyId.trim(),
            unitId = d.unitId.trim(),
            tenantReference = d.tenant.trim(),
            rentMonth = transaction.bookingDate.take(7),
            note = d.note.trim(),
            receiptId = d.receiptId
        )
    }
    val preview = BankTransactionSplitPolicy.preview(transaction, links, assignments, positions())
    val already = BankTransactionSplitPolicy.allocatedAmount(transaction.transactionId, links, assignments)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (previewMode) "Aufteilung prüfen" else "Buchung aufteilen") },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("Buchungsdatum: ${transaction.bookingDate}")
                    Text("Gegenpartei: ${transaction.counterparty.ifBlank { "–" }}")
                    Text("Verwendungszweck: ${transaction.purpose.ifBlank { "–" }}")
                    Text("Originalbetrag: ${euro.format(transaction.absoluteAmount)}", fontWeight = FontWeight.Bold)
                    Text("Bereits zugeordnet: ${euro.format(already)}")
                    Text("Verbleibender Rest vor Aufteilung: ${euro.format((transaction.absoluteAmount - already).coerceAtLeast(0.0))}")
                }
                items(drafts.indices.toList()) { index ->
                    val draft = drafts[index]
                    Card(border = BorderStroke(1.dp, BorderColor)) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text("Teilposition ${index + 1}", fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(value = draft.amount, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(amount = value) } }, label = { Text("Betrag") }, enabled = !previewMode)
                            OutlinedButton(onClick = { if (!previewMode) categoryMenu = index }, enabled = !previewMode) { Text("Kategorie: ${BankSplitPaymentType.label(draft.paymentType)}") }
                            DropdownMenu(expanded = categoryMenu == index, onDismissRequest = { categoryMenu = null }) {
                                BankSplitPaymentType.all.filter { BankSplitPaymentType.directionAllowed(it, transaction) }.forEach { type ->
                                    DropdownMenuItem(text = { Text(BankSplitPaymentType.label(type)) }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(paymentType = type, receiptId = if (type == BankSplitPaymentType.RECEIPT) draft.receiptId else null) }
                                        categoryMenu = null
                                    })
                                }
                            }
                            OutlinedTextField(value = draft.propertyId, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(propertyId = value) } }, label = { Text("Immobilie") }, enabled = !previewMode)
                            if (draft.paymentType in BankSplitPaymentType.rentScoped) {
                                OutlinedButton(onClick = { if (!previewMode) unitMenu = index }, enabled = !previewMode) { Text("Wohneinheit: ${draft.unitId.ifBlank { "auswählen" }}") }
                                DropdownMenu(expanded = unitMenu == index, onDismissRequest = { unitMenu = null }) {
                                    units.forEach { unit -> DropdownMenuItem(text = { Text(unit.label.ifBlank { unit.name }) }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(unitId = unit.unitId.ifBlank { unit.name }) }
                                        unitMenu = null
                                    }) }
                                }
                                OutlinedTextField(value = draft.tenant, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(tenant = value) } }, label = { Text("Mieter / Mietverhältnis") }, enabled = !previewMode)
                            }
                            if (draft.paymentType == BankSplitPaymentType.RECEIPT) {
                                val selected = receipts.firstOrNull { it.id == draft.receiptId }
                                OutlinedButton(onClick = { if (!previewMode) receiptMenu = index }, enabled = !previewMode) { Text("Beleg: ${selected?.let { "${it.datum} • ${it.aussteller} • ${euro.format(it.bruttobetrag)}" } ?: "auswählen"}") }
                                DropdownMenu(expanded = receiptMenu == index, onDismissRequest = { receiptMenu = null }) {
                                    receipts.take(30).forEach { receipt -> DropdownMenuItem(text = { Text("${receipt.datum} • ${receipt.aussteller} • ${euro.format(receipt.bruttobetrag)}") }, onClick = {
                                        drafts = drafts.toMutableList().also { it[index] = draft.copy(receiptId = receipt.id, propertyId = draft.propertyId.ifBlank { receipt.propertyId }) }
                                        receiptMenu = null
                                    }) }
                                }
                            }
                            OutlinedTextField(value = draft.note, onValueChange = { value -> drafts = drafts.toMutableList().also { it[index] = draft.copy(note = value) } }, label = { Text("Notiz (optional)") }, enabled = !previewMode)
                            if (!previewMode && drafts.size > 1) TextButton(onClick = { drafts = drafts.toMutableList().also { it.removeAt(index) } }) { Text("Position entfernen") }
                        }
                    }
                }
                if (!previewMode) item { OutlinedButton(onClick = { drafts = drafts + BankSplitDraft(propertyId = defaultPropertyId, unitId = transaction.unitId) }, modifier = Modifier.fillMaxWidth()) { Text("+ Position hinzufügen") } }
                item {
                    Text("Gesamtbetrag: ${euro.format(preview.originalAmount)}", fontWeight = FontWeight.SemiBold)
                    Text("Neue Zuordnungen: ${euro.format(preview.newAllocations)}")
                    Text("Verbleibender Rest: ${euro.format(preview.remainingAmount)}", fontWeight = FontWeight.Bold)
                    Text("Status danach: ${preview.statusAfter}")
                    if (!preview.valid) Text(preview.message, color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (previewMode) Button(onClick = { onConfirm(positions()) }, enabled = preview.valid) { Text("Aufteilung bestätigen") }
            else Button(onClick = { previewMode = true }, enabled = preview.valid) { Text("Aufteilung prüfen") }
        },
        dismissButton = {
            if (previewMode) TextButton(onClick = { previewMode = false }) { Text("Zurück") }
            else TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}
''')

Path('app/src/test/java/com/example/data/BankTransactionSplitServiceTest.kt').write_text(r'''package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankTransactionSplitServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var service: BankPhase2DService

    @Before fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        service = BankPhase2DService(db) { "2026-09-10T18:00:00Z" }
    }
    @After fun tearDown() = db.close()

    private fun tx(amount: Double = 1500.0, status: String = BankReconciliationStatus.OPEN, property: String = "p1") =
        BankTransaction("t", "a", "2026-09-10", amount = amount, counterparty = "Synthetic Tenant", purpose = "Synthetic payment", propertyId = property, reconciliationStatus = status)
    private fun split(amount: Double, type: String, property: String = "p1", unit: String = "u1", tenant: String = "Synthetic Tenant", note: String = "") =
        BankManualSplitPosition("t", amount, type, property, unit, tenant, "2026-09", note)
    private fun receipt(id: Int, amount: Double = 100.0, property: String = "p1") = Receipt(id=id, aussteller="Synthetic Vendor", datum="2026-09-09", uhrzeit="", bruttobetrag=amount, hauptkategorie="Kosten", unterkategorie="Test", kontoNr="", beschreibung="Synthetic", internalId="r$id", propertyId=property)

    @Test fun rentAndDepositFullyMatch1500() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val original = db.bankDao().getTransaction("t")!!
        val result = service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT), split(500.0, BankSplitPaymentType.DEPOSIT)), true)
        assertTrue(result.success)
        val saved = db.bankRentAssignmentDao().getForTransaction("t")
        assertEquals(2, saved.size); assertEquals(setOf("MIETE", "KAUTION"), saved.map { it.paymentType }.toSet())
        assertEquals(1500.0, saved.sumOf { it.allocatedAmount }, 0.001)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(original.amount, db.bankDao().getTransaction("t")!!.amount, 0.0)
    }

    @Test fun partialRentLeaves500() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        assertEquals(BankReconciliationStatus.PARTIAL, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(500.0, BankTransactionSplitPolicy.remainingAmount(db.bankDao().getTransaction("t")!!, emptyList(), db.bankRentAssignmentDao().getForTransaction("t")), 0.001)
    }

    @Test fun overAllocationRejectsWithoutPartialPersistence() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val result = service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT), split(600.0, BankSplitPaymentType.DEPOSIT)), true)
        assertFalse(result.success); assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
    }

    @Test fun existing500Plus1000Completes() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(500.0, BankSplitPaymentType.DEPOSIT)), true).success)
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun doubleConfirmationIsIdempotent() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        val positions = listOf(split(1000.0, BankSplitPaymentType.RENT), split(500.0, BankSplitPaymentType.DEPOSIT))
        assertTrue(service.confirmManualSplit(positions, true).success)
        assertTrue(service.confirmManualSplit(positions, true).success)
        assertEquals(2, db.bankRentAssignmentDao().getForTransaction("t").size)
    }

    @Test fun noReceiptRequiredIsProtected() = runTest {
        db.bankDao().insertTransactions(listOf(tx(status=BankReconciliationStatus.NO_RECEIPT_REQUIRED)))
        assertFalse(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT)), true).success)
        assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
        assertEquals(BankReconciliationStatus.NO_RECEIPT_REQUIRED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun changeAmountUpdatesRestAndStatus() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT)), true).success)
        val id = db.bankRentAssignmentDao().getForTransaction("t").single().assignmentId
        assertTrue(service.changeManualSplitAssignment(id,1500.0,true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun unlinkUpdatesRestAndStatus() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT)), true).success)
        val id = db.bankRentAssignmentDao().getForTransaction("t").single().assignmentId
        assertTrue(service.unlinkManualSplitAssignment(id).success)
        assertEquals(BankReconciliationStatus.OPEN, db.bankDao().getTransaction("t")!!.reconciliationStatus)
        assertEquals(1500.0, db.bankDao().getTransaction("t")!!.absoluteAmount, 0.0)
    }

    @Test fun propertyConflictIsBlocked() = runTest {
        db.bankDao().insertTransactions(listOf(tx(property="p1")))
        assertFalse(service.confirmManualSplit(listOf(split(1500.0, BankSplitPaymentType.RENT, property="p2")), true).success)
        assertTrue(db.bankRentAssignmentDao().getForTransaction("t").isEmpty())
    }

    @Test fun centRoundingUsesExistingPolicy() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=10.01)))
        val p = split(10.005, BankSplitPaymentType.RENT)
        val preview = BankTransactionSplitPolicy.preview(db.bankDao().getTransaction("t")!!, emptyList(), emptyList(), listOf(p))
        assertEquals(BankAllocationPolicy.roundMoney(10.005), preview.newAllocations, 0.0)
        assertTrue(preview.valid)
    }

    @Test fun existingOneToOneReceiptStillWorksWithManualRemainderPolicy() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=-100.0)))
        db.receiptDao().insertAll(listOf(receipt(1,100.0)))
        assertTrue(service.confirmManualAllocations(listOf(BankProposedAllocation("t",1,"r1",100.0)),true).success)
        assertEquals(1, db.bankDao().getLinksForTransaction("t").size)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun phase2DCombinationStillWorksAndCannotOverallocateManualSplit() = runTest {
        db.bankDao().insertTransactions(listOf(tx(amount=-300.0)))
        db.receiptDao().insertAll(listOf(receipt(1,100.0), receipt(2,200.0)))
        val suggestion = BankCombinationMatcher.oneTransactionToManyReceipts(db.bankDao().getTransaction("t")!!, db.receiptDao().getAllReceiptsList(), emptyList()).first()
        assertTrue(service.confirmCombination(suggestion,true).success)
        assertEquals(BankReconciliationStatus.MATCHED, db.bankDao().getTransaction("t")!!.reconciliationStatus)
    }

    @Test fun sameTargetDifferentAmountIsNeverSilentlyOverwrittenAndNotePersists() = runTest {
        db.bankDao().insertTransactions(listOf(tx()))
        assertTrue(service.confirmManualSplit(listOf(split(1000.0, BankSplitPaymentType.RENT, note="Synthetic note")),true).success)
        assertFalse(service.confirmManualSplit(listOf(split(900.0, BankSplitPaymentType.RENT, note="Synthetic note")),true).success)
        assertEquals(1000.0, db.bankRentAssignmentDao().getForTransaction("t").single().allocatedAmount,0.0)
        assertEquals("Synthetic note", db.bankRentAssignmentDao().getForTransaction("t").single().note)
    }
}
''')

Path('app/src/test/java/com/example/data/BankTransactionSplitMigrationBackupTest.kt').write_text(r'''package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankTransactionSplitMigrationBackupTest {
    @Test fun migration27To28AddsNoteWithSafeDefault() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null)
            .callback(object : SupportSQLiteOpenHelper.Callback(27) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE bank_rent_assignments (assignmentId TEXT NOT NULL PRIMARY KEY, transactionId TEXT NOT NULL, propertyId TEXT NOT NULL, unitId TEXT NOT NULL, rentMonth TEXT NOT NULL, tenantReference TEXT NOT NULL, allocatedAmount REAL NOT NULL, paymentType TEXT NOT NULL, status TEXT NOT NULL, source TEXT NOT NULL, receiptId INTEGER, createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase
        MIGRATION_27_28.migrate(db)
        db.execSQL("INSERT INTO bank_rent_assignments VALUES ('a','t','p','u','2026-09','tenant',10.0,'MIETE','CONFIRMED','MANUAL',NULL,'c','u','')")
        db.query("SELECT note FROM bank_rent_assignments WHERE assignmentId='a'").use { cursor ->
            assertTrue(cursor.moveToFirst()); assertEquals("", cursor.getString(0))
        }
        helper.close()
    }

    @Test fun supplementalBackupRoundTripsManualNote() = runTest {
        assertEquals(11, SupplementalDriveBackup.SCHEMA_VERSION)
        val context: Context = ApplicationProvider.getApplicationContext()
        val source = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        source.bankRentAssignmentDao().upsert(BankRentAssignment("a","t","p","u","2026-09","tenant",10.0,"KAUTION",createdAt="c",updatedAt="u",note="Synthetic note"))
        val payload: JSONObject = SupplementalDriveBackup.createPayload(context, source)
        val target = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        SupplementalDriveBackup.restorePayload(context, target, payload)
        assertEquals("Synthetic note", target.bankRentAssignmentDao().getById("a")!!.note)
        source.close(); target.close()
    }
}
''')

print('bank transaction split patch applied')
