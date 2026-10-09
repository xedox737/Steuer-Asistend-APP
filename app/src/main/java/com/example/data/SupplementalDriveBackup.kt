package com.example.data

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.time.Instant
import androidx.room.withTransaction
import com.example.api.GoogleDriveClient
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.first

object SupplementalDriveBackup {
    private const val ENTITY_TYPE = "supplementalBackup"
    private const val FILE_NAME = "supplementalBackup.json"
    internal const val SCHEMA_VERSION = 14
    data class Result(val success: Boolean, val message: String)

    suspend fun backup(context: Context, database: AppDatabase, accessToken: String, systemFolderId: String): Result =
        try {
            val root = createPayload(context, database)
            val existing = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
            val result = if (existing != null) GoogleDriveClient.updateJson(accessToken, existing.id, root.toString(4))
            else GoogleDriveClient.uploadJson(
                accessToken, systemFolderId, FILE_NAME, root.toString(4),
                mapOf("appName" to "ImmobilienBelegApp", "entityType" to ENTITY_TYPE, "schemaVersion" to SCHEMA_VERSION.toString())
            )
            Result(result.success, result.errorMessage ?: if (result.success) "Zusatzdaten gesichert" else "Zusatzdaten-Backup fehlgeschlagen")
        } catch (e: Exception) {
            Result(false, e.message ?: "Zusatzdaten-Backup fehlgeschlagen")
        }

    suspend fun restore(
        context: Context,
        database: AppDatabase,
        accessToken: String,
        systemFolderId: String,
        replaceManagedDocuments: Boolean = false,
        restoreMode: RestoreMode = if (replaceManagedDocuments) RestoreMode.REPLACE_FULL else RestoreMode.MERGE
    ): Result =
        try {
            val file = GoogleDriveClient.findFileByAppProperty(accessToken, systemFolderId, ENTITY_TYPE)
                ?: return Result(true, "Keine Zusatzdaten-Sicherung vorhanden")
            restorePayload(
                context,
                database,
                JSONObject(GoogleDriveClient.downloadJson(accessToken, file.id)),
                replaceManagedDocuments,
                restoreMode
            )
            Result(true, "Zusatzdaten einschließlich Fahrtenbuch wiederhergestellt")
        } catch (e: Exception) {
            Result(false, e.message ?: "Zusatzdaten-Wiederherstellung fehlgeschlagen")
        }

    internal suspend fun createPayload(context: Context, database: AppDatabase): JSONObject = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("loans", JSONArray().apply { database.loanDao().getAllLoans().forEach { put(it.toJson()) } })
        put("properties", JSONArray().apply { database.propertyDao().getAllProperties().forEach { put(it.toBackupJson()) } })
        put("logbookTrips", JSONArray().apply { database.logbookDao().getAllTrips().forEach { put(it.toJson()) } })
        put("exportAuditRuns", JSONArray().apply { database.exportAuditDao().getAllRunsFlow().first().forEach { put(it.toBackupJson()) } })
        put("standardRoutes", JSONArray().apply { database.logbookDao().getAllStandardRoutes().forEach { put(it.toJson()) } })
        put("managedDocuments", JSONArray().apply { database.managedDocumentDao().getAll().forEach { put(it.toBackupJson()) } })
        put("bankAccounts", JSONArray().apply { database.bankDao().getAllAccounts().forEach { put(it.toBackupJson()) } })
        put("bankTransactions", JSONArray().apply { database.bankDao().getAllTransactions().forEach { put(it.toBackupJson()) } })
        put("bankReceiptLinks", JSONArray().apply { database.bankDao().getAllLinks().forEach { put(it.toBackupJson()) } })
        put("bankLearningRules", JSONArray().apply { database.bankLearningRuleDao().getAllRules().forEach { put(it.toBackupJson()) } })
        put("bankRuleEvidence", JSONArray().apply { database.bankLearningRuleDao().getAllEvidence().forEach { put(it.toBackupJson()) } })
        put("bankRentAssignments", JSONArray().apply { database.bankRentAssignmentDao().getAll().forEach { put(it.toBackupJson()) } })
        put("bankLoanAssignments", JSONArray().apply { database.bankLoanAssignmentDao().getAll().forEach { put(it.toBackupJson()) } })
        put("bankRecurringPatterns", JSONArray().apply { database.bankRecurringPatternDao().getAll().forEach { put(it.toBackupJson()) } })
        PersistentPreferenceInventory.writePayload(context, this)
    }

    internal suspend fun restorePayload(
        context: Context,
        database: AppDatabase,
        root: JSONObject,
        replaceManagedDocuments: Boolean = false,
        restoreMode: RestoreMode = if (replaceManagedDocuments) RestoreMode.REPLACE_FULL else RestoreMode.MERGE
    ) {
        require(root.optInt("schemaVersion", 1) <= SCHEMA_VERSION) { "Neuere Sicherung: Bitte die App aktualisieren." }
        PersistentPreferenceInventory.validatePayload(root)
        // A malformed/null array is not an intentional empty snapshot, especially for replace.
        listOf("loans", "properties", "logbookTrips", "standardRoutes", "exportAuditRuns", "managedDocuments",
            "bankAccounts", "bankTransactions", "bankReceiptLinks", "bankLearningRules", "bankRuleEvidence",
            "bankRentAssignments", "bankLoanAssignments", "bankRecurringPatterns").forEach { key ->
            if (root.has(key)) root.getJSONArray(key)
        }
        // Validate/decode photos before touching Room; filenames depend on content, never a supplied path.
        val propertyArray = root.optJSONArray("properties") ?: JSONArray()
        val restoredProperties = (0 until propertyArray.length()).map { index ->
            val json = propertyArray.getJSONObject(index)
            val property = json.toPropertyMetadata()
            val encoded = json.optString("imageBase64", "")
            val current = database.propertyDao().getPropertyByPropertyId(property.propertyId)
            val localImage = if (encoded.isNotBlank()) {
                val bytes = Base64.decode(encoded, Base64.NO_WRAP)
                require(bytes.isNotEmpty()) { "Leeres Objektbild in der Sicherung." }
                val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                    .joinToString("") { "%02x".format(it) }
                val folder = File(context.filesDir, "property-images").apply { mkdirs() }
                val owner = UUID.nameUUIDFromBytes(property.propertyId.toByteArray(Charsets.UTF_8))
                val file = File(folder, "$owner-$digest.jpg")
                if (!file.isFile || !file.readBytes().contentEquals(bytes)) {
                    val staging = File.createTempFile("restore-", ".tmp", folder)
                    try {
                        staging.writeBytes(bytes)
                        check(staging.renameTo(file)) { "Objektbild konnte nicht wiederhergestellt werden." }
                    } finally { staging.delete() }
                }
                file.absolutePath
            } else {
                current?.bildPfad?.takeIf { File(it).isFile }
                    ?: property.bildPfad.takeIf { it.isNotBlank() && File(it).isFile }.orEmpty()
            }
            if (restoreMode == RestoreMode.MERGE && current != null) {
                current.copy(bildPfad = current.bildPfad.takeIf { File(it).isFile } ?: localImage)
            } else property.copy(bildPfad = localImage)
        }
        val existingDocuments = database.managedDocumentDao().getAll().associateBy { it.documentId }
        database.withTransaction {
            val localLoans = database.loanDao().getAllLoans().associateBy { it.id }
            val loans = root.optJSONArray("loans") ?: JSONArray()
            for (index in 0 until loans.length()) {
                val incoming = loans.getJSONObject(index).toLoan()
                if (RestoreConflictPolicy.shouldImport(restoreMode, localLoans.containsKey(incoming.id))) {
                    database.loanDao().upsertLoan(incoming)
                }
            }
            restoredProperties.forEach { database.propertyDao().upsertRestoredPropertyMetadata(it) }
            val trips = root.optJSONArray("logbookTrips") ?: JSONArray()
            for (index in 0 until trips.length()) {
                val incoming = trips.getJSONObject(index).toTrip()
                val local = database.logbookDao().getTrip(incoming.id)
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.logbookDao().upsertTrip(incoming)
                }
            }
            val auditRuns = root.optJSONArray("exportAuditRuns") ?: JSONArray()
            for (index in 0 until auditRuns.length()) {
                val incoming = auditRuns.getJSONObject(index).toExportAuditRun()
                if (RestoreConflictPolicy.shouldImport(restoreMode, database.exportAuditDao().getRunById(incoming.exportlaufId) != null)) {
                    database.exportAuditDao().insertRun(incoming)
                }
            }
            val localRoutes = database.logbookDao().getAllStandardRoutes().associateBy { it.id }
            val routes = root.optJSONArray("standardRoutes") ?: JSONArray()
            for (index in 0 until routes.length()) {
                val incoming = routes.getJSONObject(index).toStandardRoute()
                val local = localRoutes[incoming.id]
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.logbookDao().upsertStandardRoute(incoming)
                }
            }
            if (replaceManagedDocuments && root.has("managedDocuments")) {
                database.managedDocumentDao().clearSearchIndex()
                database.managedDocumentDao().deleteAllDocuments()
                database.managedDocumentDao().clearMigrationJournal()
            }
            val documents = root.optJSONArray("managedDocuments") ?: JSONArray()
            for (index in 0 until documents.length()) {
                val backupDocument = documents.getJSONObject(index).toManagedDocument()
                val localDocument = existingDocuments[backupDocument.documentId]
                val mergedDocument = mergeManagedDocument(
                    context = context,
                    local = localDocument,
                    backup = backupDocument,
                    restoreMode = restoreMode
                )
                database.managedDocumentDao().upsert(mergedDocument)
            }
            val bankAccounts = root.optJSONArray("bankAccounts") ?: JSONArray()
            for (index in 0 until bankAccounts.length()) {
                val incoming = bankAccounts.getJSONObject(index).toBankAccount()
                val local = database.bankDao().getAccount(incoming.accountId)
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankDao().upsertAccount(incoming)
                }
            }
            val localTransactions = database.bankDao().getAllTransactions().associateBy { it.transactionId }
            val bankTransactions = root.optJSONArray("bankTransactions") ?: JSONArray()
            val incomingTransactions = (0 until bankTransactions.length()).map {
                bankTransactions.getJSONObject(it).toBankTransaction()
            }.associateBy { it.transactionId }
            incomingTransactions.values.forEach { incoming ->
                val local = localTransactions[incoming.transactionId]
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankDao().upsertTransaction(incoming)
                }
            }
            // A link/split can have been removed or moved after this backup. Do not resurrect
            // relationships of a transaction whose newer local state we just preserved.
            fun mayAddRelationship(transactionId: String): Boolean {
                if (restoreMode != RestoreMode.MERGE) return true
                val local = localTransactions[transactionId] ?: return true
                val backup = incomingTransactions[transactionId] ?: return false
                return local == backup || RestoreConflictPolicy.backupIsNewer(local.updatedAt, backup.updatedAt)
            }
            val bankLinks = root.optJSONArray("bankReceiptLinks") ?: JSONArray()
            for (index in 0 until bankLinks.length()) {
                val restored = bankLinks.getJSONObject(index).toBankReceiptLink()
                val local = database.bankDao().getLink(restored.linkId)
                if (!RestoreConflictPolicy.shouldImport(restoreMode, local != null) || !mayAddRelationship(restored.transactionId)) continue
                val resolvedReceiptId = restored.receiptInternalId.takeIf { it.isNotBlank() }
                    ?.let { database.receiptDao().getReceiptByInternalId(it)?.id }
                    ?: restored.receiptId
                database.bankDao().upsertLink(restored.copy(receiptId = resolvedReceiptId))
            }
            // Phase 2A learning data is restored on every normal backup restore. It must not
            // depend on the optional destructive replacement of managed documents above.
            val learningRules = root.optJSONArray("bankLearningRules") ?: JSONArray()
            for (index in 0 until learningRules.length()) {
                val incoming = learningRules.getJSONObject(index).toBankLearningRule()
                val local = database.bankLearningRuleDao().getRule(incoming.ruleId)
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankLearningRuleDao().upsertRule(incoming)
                }
            }
            val learningEvidence = root.optJSONArray("bankRuleEvidence") ?: JSONArray()
            for (index in 0 until learningEvidence.length()) database.bankLearningRuleDao().insertEvidence(learningEvidence.getJSONObject(index).toBankRuleEvidence())
            val rentAssignments = root.optJSONArray("bankRentAssignments") ?: JSONArray()
            for (index in 0 until rentAssignments.length()) {
                val incoming = rentAssignments.getJSONObject(index).toBankRentAssignment()
                val local = database.bankRentAssignmentDao().getById(incoming.assignmentId)
                if ((local != null || mayAddRelationship(incoming.transactionId)) &&
                    RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankRentAssignmentDao().upsert(incoming)
                }
            }
            val loanAssignments = root.optJSONArray("bankLoanAssignments") ?: JSONArray()
            for (index in 0 until loanAssignments.length()) {
                val incoming = loanAssignments.getJSONObject(index).toBankLoanAssignment()
                val local = database.bankLoanAssignmentDao().getForTransaction(incoming.transactionId)
                if ((local != null || mayAddRelationship(incoming.transactionId)) &&
                    RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankLoanAssignmentDao().upsert(incoming)
                }
            }
            val recurringPatterns = root.optJSONArray("bankRecurringPatterns") ?: JSONArray()
            for (index in 0 until recurringPatterns.length()) {
                val incoming = recurringPatterns.getJSONObject(index).toBankRecurringPattern()
                val local = database.bankRecurringPatternDao().get(incoming.patternId)
                if (RestoreConflictPolicy.shouldImport(restoreMode, local != null, local?.updatedAt.orEmpty(), incoming.updatedAt)) {
                    database.bankRecurringPatternDao().upsert(incoming)
                }
            }
            database.managedDocumentDao().clearSearchIndex()
            database.managedDocumentDao().getAll().forEach { document ->
                database.managedDocumentDao().insertSearchEntry(DocumentSearchFts(document.documentId, DocumentSearchTextBuilder.build(document)))
            }
            val repository = ReceiptRepository(database.receiptDao(), database.propertyDao(), database.receiptEntityDao(),
                database.belegDao(), database.exportAuditDao(), database.receiptDocumentDao(), database.managedDocumentDao())
            database.receiptDao().getAllReceiptsIncludingDeletedList().forEach { receipt ->
                val paths = ReceiptOriginalChain.localPaths(receipt, database.managedDocumentDao().getAllByReceiptId(receipt.internalId))
                if (paths != receipt.imageUrl) repository.insert(receipt.copy(imageUrl = paths))
            }
        }
        // Preferences are intentionally written only after the Room transaction committed.
        PersistentPreferenceInventory.restorePayload(context, root, restoreMode)
    }

    private fun ExportAuditRun.toBackupJson() = JSONObject().apply {
        put("exportlaufId", exportlaufId); put("timestamp", timestamp); put("user", user)
        put("propertyName", propertyName); put("periodStart", periodStart); put("periodEnd", periodEnd)
        put("filterSummary", filterSummary); put("exportierteReceiptIdsJson", exportierteReceiptIdsJson)
        put("ausgeschlosseneReceiptIdsJson", ausgeschlosseneReceiptIdsJson)
        put("kanzleiprofilNameVersion", kanzleiprofilNameVersion); put("zipFileName", zipFileName)
        put("zipFileSizeBytes", zipFileSizeBytes); put("zipSha256", zipSha256); put("status", status)
        put("totalAmount", totalAmount); put("bookingCount", bookingCount); put("warningsCount", warningsCount)
        put("logMessage", logMessage)
    }

    private fun JSONObject.toExportAuditRun() = ExportAuditRun(
        exportlaufId = getString("exportlaufId"), timestamp = getLong("timestamp"), user = optString("user", ""),
        propertyName = optString("propertyName", ""), periodStart = optString("periodStart", ""),
        periodEnd = optString("periodEnd", ""), filterSummary = optString("filterSummary", ""),
        exportierteReceiptIdsJson = optString("exportierteReceiptIdsJson", "[]"),
        ausgeschlosseneReceiptIdsJson = optString("ausgeschlosseneReceiptIdsJson", "[]"),
        kanzleiprofilNameVersion = optString("kanzleiprofilNameVersion", ""), zipFileName = optString("zipFileName", ""),
        zipFileSizeBytes = optLong("zipFileSizeBytes", 0L), zipSha256 = optString("zipSha256", ""),
        status = optString("status", "SUCCESS"), totalAmount = optDouble("totalAmount", 0.0),
        bookingCount = optInt("bookingCount", 0), warningsCount = optInt("warningsCount", 0), logMessage = optString("logMessage", "")
    )

    private fun BankAccount.toBackupJson() = JSONObject().apply {
        put("accountId", accountId); put("displayName", displayName); put("bankName", bankName)
        put("accountHolder", accountHolder); put("iban", iban); put("currency", currency); put("source", source); put("active", active)
        put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toBankAccount() = BankAccount(
        accountId = optString("accountId", ""), displayName = optString("displayName", ""),
        bankName = optString("bankName", ""), accountHolder = optString("accountHolder", ""), iban = optString("iban", ""),
        currency = optString("currency", "EUR"), source = optString("source", "CSV"),
        active = optBoolean("active", true), createdAt = optString("createdAt", ""),
        updatedAt = optString("updatedAt", "")
    )

    private fun BankTransaction.toBackupJson() = JSONObject().apply {
        put("transactionId", transactionId); put("accountId", accountId); put("bookingDate", bookingDate)
        put("valueDate", valueDate); put("amount", amount); put("currency", currency)
        put("counterparty", counterparty); put("counterpartyIban", counterpartyIban); put("purpose", purpose)
        put("bankReference", bankReference); put("source", source)
        put("propertyId", propertyId); put("unitId", unitId)
        put("category", category); put("subcategory", subcategory)
        put("importFileName", importFileName); put("importRunId", importRunId)
        put("reconciliationStatus", reconciliationStatus); put("noReceiptReason", noReceiptReason)
        put("classification", classification); put("transferCounterAccountId", transferCounterAccountId)
        put("linkedTransferTransactionId", linkedTransferTransactionId); put("reviewState", reviewState)
        put("importedAt", importedAt); put("updatedAt", updatedAt)
    }

    private fun JSONObject.toBankTransaction() = BankTransaction(
        transactionId = optString("transactionId", ""), accountId = optString("accountId", ""),
        bookingDate = optString("bookingDate", ""), valueDate = optString("valueDate", ""),
        amount = optDouble("amount", 0.0), currency = optString("currency", "EUR"),
        counterparty = optString("counterparty", ""), counterpartyIban = optString("counterpartyIban", ""),
        purpose = optString("purpose", ""), bankReference = optString("bankReference", ""),
        source = optString("source", "CSV"), propertyId = optString("propertyId", ""),
        unitId = optString("unitId", ""), category = optString("category", ""),
        subcategory = optString("subcategory", ""), importFileName = optString("importFileName", ""),
        importRunId = optString("importRunId", ""),
        reconciliationStatus = optString("reconciliationStatus", BankReconciliationStatus.OPEN),
        noReceiptReason = optString("noReceiptReason", ""),
        classification = BankTransactionClassification.normalize(optString("classification", BankTransactionClassification.NORMAL)),
        transferCounterAccountId = optString("transferCounterAccountId", ""),
        linkedTransferTransactionId = optString("linkedTransferTransactionId", ""),
        reviewState = optString("reviewState", BankReviewState.OPEN).takeIf { it in setOf(BankReviewState.OPEN, BankReviewState.DONE) } ?: BankReviewState.OPEN,
        importedAt = optString("importedAt", ""), updatedAt = optString("updatedAt", "")
    )

    private fun BankLearningRule.toBackupJson() = JSONObject().apply {
        put("ruleId",ruleId); put("displayName",displayName); put("enabled",enabled); put("state",state); put("ruleType",ruleType); put("transactionDirection",transactionDirection); put("counterpartyPattern",counterpartyPattern); put("counterpartyIbanPattern",counterpartyIbanPattern); put("purposeTerms",purposeTerms); amountMin?.let{put("amountMin",it)}; amountMax?.let{put("amountMax",it)}; put("currency",currency); put("accountId",accountId); put("propertyId",propertyId); put("unitId",unitId); put("receiptVendorTarget",receiptVendorTarget); put("receiptCategoryTarget",receiptCategoryTarget); put("receiptSubcategoryTarget",receiptSubcategoryTarget); put("paymentMethodTarget",paymentMethodTarget); put("evidenceCount",evidenceCount); put("successCount",successCount); put("rejectionCount",rejectionCount); put("confidence",confidence); put("source",source); put("createdAt",createdAt); put("updatedAt",updatedAt); put("lastMatchedAt",lastMatchedAt)
    }
    private fun JSONObject.toBankLearningRule() = BankLearningRule(ruleId=optString("ruleId",""),displayName=optString("displayName","Bankregel"),enabled=optBoolean("enabled",false),state=optString("state",BankRuleState.PROPOSED),ruleType=optString("ruleType",BankRuleType.COMBINED),transactionDirection=optString("transactionDirection",BankRuleDirection.ANY),counterpartyPattern=optString("counterpartyPattern",""),counterpartyIbanPattern=optString("counterpartyIbanPattern",""),purposeTerms=optString("purposeTerms",""),amountMin=if(has("amountMin"))optDouble("amountMin") else null,amountMax=if(has("amountMax"))optDouble("amountMax") else null,currency=optString("currency","EUR"),accountId=optString("accountId",""),propertyId=optString("propertyId",""),unitId=optString("unitId",""),receiptVendorTarget=optString("receiptVendorTarget",""),receiptCategoryTarget=optString("receiptCategoryTarget",""),receiptSubcategoryTarget=optString("receiptSubcategoryTarget",""),paymentMethodTarget=optString("paymentMethodTarget",""),evidenceCount=optInt("evidenceCount",0),successCount=optInt("successCount",0),rejectionCount=optInt("rejectionCount",0),confidence=optInt("confidence",0),source=optString("source",BankRuleSource.LEARNED_FROM_CONFIRMATIONS),createdAt=optString("createdAt",""),updatedAt=optString("updatedAt",""),lastMatchedAt=optString("lastMatchedAt",""))
    private fun BankRuleEvidence.toBackupJson() = JSONObject().apply { put("evidenceId",evidenceId);put("candidateKey",candidateKey);put("ruleId",ruleId);put("transactionId",transactionId);put("receiptId",receiptId);put("confirmed",confirmed);put("counterparty",counterparty);put("direction",direction);put("purposeTerms",purposeTerms);put("accountId",accountId);put("propertyId",propertyId);put("unitId",unitId);put("vendorTarget",vendorTarget);put("categoryTarget",categoryTarget);put("subcategoryTarget",subcategoryTarget);put("paymentMethodTarget",paymentMethodTarget);put("createdAt",createdAt) }
    private fun JSONObject.toBankRuleEvidence() = BankRuleEvidence(evidenceId=optString("evidenceId",""),candidateKey=optString("candidateKey",""),ruleId=optString("ruleId",""),transactionId=optString("transactionId",""),receiptId=optInt("receiptId",0),confirmed=optBoolean("confirmed",true),counterparty=optString("counterparty",""),direction=optString("direction",BankRuleDirection.ANY),purposeTerms=optString("purposeTerms",""),accountId=optString("accountId",""),propertyId=optString("propertyId",""),unitId=optString("unitId",""),vendorTarget=optString("vendorTarget",""),categoryTarget=optString("categoryTarget",""),subcategoryTarget=optString("subcategoryTarget",""),paymentMethodTarget=optString("paymentMethodTarget",""),createdAt=optString("createdAt",""))

    private fun BankRentAssignment.toBackupJson() = JSONObject().apply {
        put("assignmentId", assignmentId); put("transactionId", transactionId); put("propertyId", propertyId)
        put("unitId", unitId); put("rentMonth", rentMonth); put("tenantReference", tenantReference)
        put("allocatedAmount", allocatedAmount); put("paymentType", paymentType); put("status", status)
        put("source", source); receiptId?.let { put("receiptId", it) }; put("createdAt", createdAt); put("updatedAt", updatedAt); put("note", note)
    }

    private fun JSONObject.toBankRentAssignment() = BankRentAssignment(
        assignmentId = optString("assignmentId", ""), transactionId = optString("transactionId", ""),
        propertyId = optString("propertyId", ""), unitId = optString("unitId", ""), rentMonth = optString("rentMonth", ""),
        tenantReference = optString("tenantReference", ""), allocatedAmount = optDouble("allocatedAmount", 0.0),
        paymentType = optString("paymentType", "UNKLAR"), status = optString("status", BankRentAssignmentStatus.CONFIRMED),
        source = optString("source", BankRentAssignmentSource.USER_CONFIRMED),
        receiptId = if (has("receiptId") && !isNull("receiptId")) optInt("receiptId") else null,
        createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", ""), note = optString("note", "")
    )


    private fun BankLoanAssignment.toBackupJson() = JSONObject().apply {
        put("assignmentId", assignmentId); put("transactionId", transactionId); put("loanId", loanId); put("propertyId", propertyId)
        put("allocatedAmount", allocatedAmount); put("paymentType", paymentType); put("period", period); put("status", status); put("source", source)
        put("splitStatus", splitStatus); proposedInterest?.let { put("proposedInterest", it) }; proposedPrincipal?.let { put("proposedPrincipal", it) }
        put("interestBasis", interestBasis); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }
    private fun JSONObject.toBankLoanAssignment() = BankLoanAssignment(
        assignmentId=optString("assignmentId",""), transactionId=optString("transactionId",""), loanId=optInt("loanId",0), propertyId=optString("propertyId",""),
        allocatedAmount=optDouble("allocatedAmount",0.0), paymentType=optString("paymentType",BankLoanPaymentType.UNKLAR), period=optString("period",""),
        status=optString("status",BankLoanAssignmentStatus.CONFIRMED), source=optString("source",BankLoanAssignmentSource.USER_CONFIRMED),
        splitStatus=optString("splitStatus",BankLoanSplitStatus.NONE), proposedInterest=if(has("proposedInterest")&&!isNull("proposedInterest"))optDouble("proposedInterest") else null,
        proposedPrincipal=if(has("proposedPrincipal")&&!isNull("proposedPrincipal"))optDouble("proposedPrincipal") else null,
        interestBasis=optString("interestBasis",""), createdAt=optString("createdAt",""), updatedAt=optString("updatedAt","")
    )

    private fun BankRecurringPattern.toBackupJson() = JSONObject().apply {
        put("patternId",patternId);put("enabled",enabled);put("direction",direction);put("normalizedCounterparty",normalizedCounterparty);put("purposeFingerprint",purposeFingerprint)
        put("typicalAmount",typicalAmount);put("amountTolerance",amountTolerance);put("cadence",cadence);put("typicalDay",typicalDay);put("accountId",accountId);put("propertyId",propertyId)
        put("occurrenceCount",occurrenceCount);put("confidence",confidence);put("lastOccurrence",lastOccurrence);put("nextExpectedStart",nextExpectedStart);put("nextExpectedEnd",nextExpectedEnd)
        put("reasonsText",reasonsText);put("createdAt",createdAt);put("updatedAt",updatedAt)
    }
    private fun JSONObject.toBankRecurringPattern() = BankRecurringPattern(
        patternId=optString("patternId",""),enabled=optBoolean("enabled",true),direction=optString("direction",RecurringDirection.EXPENSE),
        normalizedCounterparty=optString("normalizedCounterparty",""),purposeFingerprint=optString("purposeFingerprint",""),typicalAmount=optDouble("typicalAmount",0.0),
        amountTolerance=optDouble("amountTolerance",0.0),cadence=optString("cadence",RecurringCadence.IRREGULAR),typicalDay=optInt("typicalDay",1),
        accountId=optString("accountId",""),propertyId=optString("propertyId",""),occurrenceCount=optInt("occurrenceCount",0),confidence=optInt("confidence",0),
        lastOccurrence=optString("lastOccurrence",""),nextExpectedStart=optString("nextExpectedStart",""),nextExpectedEnd=optString("nextExpectedEnd",""),
        reasonsText=optString("reasonsText",""),createdAt=optString("createdAt",""),updatedAt=optString("updatedAt","")
    )

    private fun BankReceiptLink.toBackupJson() = JSONObject().apply {
        put("linkId", linkId); put("transactionId", transactionId); put("receiptId", receiptId)
        put("receiptInternalId", receiptInternalId); put("allocatedAmount", allocatedAmount)
        put("status", status); put("source", source); put("createdAt", createdAt)
    }

    private fun JSONObject.toBankReceiptLink() = BankReceiptLink(
        linkId = optString("linkId", ""), transactionId = optString("transactionId", ""),
        receiptId = optInt("receiptId", 0), receiptInternalId = optString("receiptInternalId", ""),
        allocatedAmount = optDouble("allocatedAmount", 0.0),
        status = optString("status", BankLinkStatus.CONFIRMED),
        source = optString("source", BankLinkSource.NUTZER_BESTAETIGT), createdAt = optString("createdAt", "")
    )

    private fun Loan.toJson() = JSONObject().apply {
        put("id", id); put("bezeichnung", bezeichnung); put("bank", bank)
        put("darlehensbetrag", darlehensbetrag); put("restschuld", restschuld)
        put("sollzinsProzent", sollzinsProzent); put("tilgungProzent", tilgungProzent)
        put("monatlicheRate", monatlicheRate); put("startDatum", startDatum)
        put("zinsbindungBis", zinsbindungBis); put("laufzeitBis", laufzeitBis)
        put("vermietungsanteilProzent", vermietungsanteilProzent); put("notiz", notiz); put("aktiv", aktiv)
        put("propertyId", propertyId)
    }

    private fun JSONObject.toLoan() = Loan(
        id = optInt("id", 0), bezeichnung = optString("bezeichnung", ""), bank = optString("bank", ""),
        darlehensbetrag = optDouble("darlehensbetrag", 0.0), restschuld = optDouble("restschuld", 0.0),
        sollzinsProzent = optDouble("sollzinsProzent", 0.0), tilgungProzent = optDouble("tilgungProzent", 0.0),
        monatlicheRate = optDouble("monatlicheRate", 0.0), startDatum = optString("startDatum", ""),
        zinsbindungBis = optString("zinsbindungBis", ""), laufzeitBis = optString("laufzeitBis", ""),
        vermietungsanteilProzent = optDouble("vermietungsanteilProzent", 100.0),
        notiz = optString("notiz", ""), aktiv = optBoolean("aktiv", true),
        propertyId = optString("propertyId", StableDocumentIdentity.LEGACY_PROPERTY_ID)
    )

    private fun PropertyMetadata.toBackupJson() = JSONObject().apply {
        put("id", id); put("propertyId", propertyId); put("name", name); put("adresse", adresse)
        put("wohnort", wohnort); put("baujahr", baujahr); put("wohnflaeche", wohnflaeche)
        put("grundstuecksgroesse", grundstuecksgroesse); put("notariellesKaufdatum", notariellesKaufdatum)
        put("uebergangNutzenLasten", uebergangNutzenLasten); put("wohneinheiten", wohneinheiten)
        put("gesamtKaufpreis", gesamtKaufpreis); put("gebaeudewert", gebaeudewert)
        put("grundUndBodenWert", grundUndBodenWert); put("kaufpreisAufteilungQuelle", kaufpreisAufteilungQuelle)
        if (bildPfad.isNotBlank()) {
            val image = File(bildPfad)
            check(image.isFile && image.length() > 0) { "Objektbild fehlt lokal. Sicherung ist unvollständig." }
            put("imageBase64", Base64.encodeToString(image.readBytes(), Base64.NO_WRAP))
        }
        put("objektart", objektart); put("status", status); put("notizen", notizen)
        put("afaShorterYears", afaShorterYears); put("afaShorterStartDate", afaShorterStartDate)
        put("afaShorterReason", afaShorterReason); put("afaShorterDocumentId", afaShorterDocumentId)
        put("afaShorterConfirmed", afaShorterConfirmed)
    }

    private fun JSONObject.toPropertyMetadata() = PropertyMetadata(
        id = optInt("id", 1), propertyId = optString("propertyId", StableDocumentIdentity.LEGACY_PROPERTY_ID),
        name = optString("name", ""), adresse = optString("adresse", ""), wohnort = optString("wohnort", ""),
        baujahr = optInt("baujahr", 0), wohnflaeche = optDouble("wohnflaeche", 0.0),
        grundstuecksgroesse = optDouble("grundstuecksgroesse", 0.0),
        notariellesKaufdatum = optString("notariellesKaufdatum", ""),
        uebergangNutzenLasten = optString("uebergangNutzenLasten", ""),
        wohneinheiten = optString("wohneinheiten", ""), gesamtKaufpreis = optDouble("gesamtKaufpreis", 0.0),
        gebaeudewert = optDouble("gebaeudewert", 0.0), grundUndBodenWert = optDouble("grundUndBodenWert", 0.0),
        kaufpreisAufteilungQuelle = optString("kaufpreisAufteilungQuelle", "MANUELL"),
        bildPfad = optString("bildPfad", ""), objektart = optString("objektart", "Mehrfamilienhaus"),
        status = optString("status", "Aktiv"), notizen = optString("notizen", ""),
        afaShorterYears = optInt("afaShorterYears", 0),
        afaShorterStartDate = optString("afaShorterStartDate", ""),
        afaShorterReason = optString("afaShorterReason", ""),
        afaShorterDocumentId = optString("afaShorterDocumentId", ""),
        afaShorterConfirmed = optBoolean("afaShorterConfirmed", false)
    )

    private fun LogbookTrip.toJson() = JSONObject().apply {
        put("id", id); put("date", date); put("time", time); put("purpose", purpose)
        put("propertyReference", propertyReference); put("startAddress", startAddress)
        put("destinationAddress", destinationAddress); put("stopsJson", stopsJson)
        put("routeMode", routeMode); put("sameReturnRoute", sameReturnRoute)
        put("taxDistanceKm", taxDistanceKm); put("kilometerSource", kilometerSource)
        putNullable("aiEstimatedKm", aiEstimatedKm); putNullable("routedKm", routedKm)
        putNullable("gpsMeasuredKm", gpsMeasuredKm); putNullable("manualKm", manualKm)
        putNullable("odometerStartKm", odometerStartKm); putNullable("odometerEndKm", odometerEndKm)
        putNullable("standardRouteId", standardRouteId); put("plausibilityStatus", plausibilityStatus)
        put("manuallyConfirmed", manuallyConfirmed); putNullable("sourceReceiptId", sourceReceiptId)
        putNullable("expenseReceiptId", expenseReceiptId); put("routeProvider", routeProvider)
        put("routeCalculatedAt", routeCalculatedAt); putNullable("routeDurationSeconds", routeDurationSeconds)
        put("correctionReason", correctionReason); put("correctionNote", correctionNote)
        put("routeSignature", routeSignature); put("note", note); put("createdAt", createdAt); put("updatedAt", updatedAt)
        put("propertyId", propertyId); put("bookingKey", bookingKey); put("cancelledAt", cancelledAt); put("historyJson", historyJson)
    }

    private fun JSONObject.toTrip() = LogbookTrip(
        id = optLong("id", 0L), date = optString("date", ""), time = optString("time", ""),
        purpose = optString("purpose", ""), propertyReference = optString("propertyReference", ""),
        startAddress = optString("startAddress", ""), destinationAddress = optString("destinationAddress", ""),
        stopsJson = optString("stopsJson", ""), routeMode = optString("routeMode", TripRouteMode.EINFACH.name),
        sameReturnRoute = optBoolean("sameReturnRoute", false), taxDistanceKm = optDouble("taxDistanceKm", 0.0),
        kilometerSource = optString("kilometerSource", KilometerSource.MANUELL.name),
        aiEstimatedKm = nullableDouble("aiEstimatedKm"), routedKm = nullableDouble("routedKm"),
        gpsMeasuredKm = nullableDouble("gpsMeasuredKm"), manualKm = nullableDouble("manualKm"),
        odometerStartKm = nullableDouble("odometerStartKm"), odometerEndKm = nullableDouble("odometerEndKm"),
        standardRouteId = nullableLong("standardRouteId"),
        plausibilityStatus = optString("plausibilityStatus", TripPlausibilityStatus.PRUEFEN.name),
        manuallyConfirmed = optBoolean("manuallyConfirmed", false),
        sourceReceiptId = nullableInt("sourceReceiptId"), expenseReceiptId = nullableInt("expenseReceiptId"),
        routeProvider = optString("routeProvider", ""), routeCalculatedAt = optString("routeCalculatedAt", ""),
        routeDurationSeconds = nullableLong("routeDurationSeconds"), correctionReason = optString("correctionReason", ""),
        correctionNote = optString("correctionNote", ""), routeSignature = optString("routeSignature", ""),
        note = optString("note", ""), createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", ""),
        propertyId = optString("propertyId", StableDocumentIdentity.LEGACY_PROPERTY_ID),
        bookingKey = optString("bookingKey", ""), cancelledAt = optString("cancelledAt", ""), historyJson = optString("historyJson", "[]")
    )

    private fun StandardRoute.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("startAddress", startAddress); put("destinationAddress", destinationAddress)
        put("stopsJson", stopsJson); put("routeMode", routeMode); put("sameReturnRoute", sameReturnRoute)
        put("distanceKm", distanceKm); put("active", active); put("routeSignature", routeSignature)
        put("sourceProvider", sourceProvider); put("createdAt", createdAt); put("updatedAt", updatedAt)
    }

    // OCR full text and local device paths are intentionally excluded. They are rebuilt locally;
    // document identity, Drive references and extraction/review metadata remain restorable.
    internal fun mergeManagedDocument(
        context: Context,
        local: ManagedDocument?,
        backup: ManagedDocument,
        restoreMode: RestoreMode
    ): ManagedDocument {
        val preservedLocalUri = when {
            local != null && isUsableLocalUri(context, local.localUri) -> local.localUri
            backup.sha256.isNotBlank() -> findManagedFileByHash(context, backup.sha256).orEmpty()
            else -> ""
        }
        if (local == null) return backup.copy(localUri = preservedLocalUri)

        val backupNewer = backupIsNewer(local.updatedAt, backup.updatedAt)
        val base = if (restoreMode == RestoreMode.MERGE && !backupNewer) local else backup
        val other = if (base === local) backup else local
        val originalFields = if (!base.receiptInternalId.isNullOrBlank()) {
            val fields = runCatching { JSONObject(base.extractedFieldsJson) }.getOrElse { JSONObject() }
            val fallback = runCatching { JSONObject(other.extractedFieldsJson) }.getOrElse { JSONObject() }
            if (!fields.has("_receiptOriginalOrder") && fallback.has("_receiptOriginalOrder")) {
                fields.put("_receiptOriginalOrder", fallback.getInt("_receiptOriginalOrder")).toString()
            } else base.extractedFieldsJson
        } else base.extractedFieldsJson

        return base.copy(
            localUri = preservedLocalUri,
            driveFileId = base.driveFileId ?: other.driveFileId,
            driveFolderId = base.driveFolderId ?: other.driveFolderId,
            sha256 = base.sha256.ifBlank { other.sha256 },
            fileSizeBytes = if (base.fileSizeBytes > 0L) base.fileSizeBytes else other.fileSizeBytes,
            originalFilename = base.originalFilename.ifBlank { other.originalFilename },
            storedFilename = base.storedFilename.ifBlank { other.storedFilename },
            mimeType = base.mimeType.takeUnless { it.isBlank() || it == "application/octet-stream" }
                ?: other.mimeType,
            extractedFieldsJson = originalFields,
            ocrText = local.ocrText.takeIf(String::isNotBlank) ?: base.ocrText,
            ocrStatus = if (local.ocrText.isNotBlank()) local.ocrStatus else base.ocrStatus
        )
    }

    private fun backupIsNewer(localUpdatedAt: String, backupUpdatedAt: String): Boolean {
        val localTime = runCatching { Instant.parse(localUpdatedAt) }.getOrNull() ?: return false
        val backupTime = runCatching { Instant.parse(backupUpdatedAt) }.getOrNull() ?: return false
        return backupTime.isAfter(localTime)
    }

    private fun isUsableLocalUri(context: Context, value: String): Boolean {
        if (value.isBlank()) return false
        return runCatching {
            when {
                value.startsWith("content://") -> context.contentResolver.openInputStream(android.net.Uri.parse(value))?.use { true } ?: false
                value.startsWith("file://") -> File(requireNotNull(android.net.Uri.parse(value).path)).isFile
                else -> File(value).isFile
            }
        }.getOrDefault(false)
    }

    private fun findManagedFileByHash(context: Context, expectedHash: String): String? {
        if (expectedHash.isBlank()) return null
        val folder = File(context.filesDir, "managed_documents")
        val files = folder.listFiles()?.filter { it.isFile }.orEmpty()
        return files.firstOrNull { file ->
            runCatching {
                val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it) }
                digest.equals(expectedHash, ignoreCase = true)
            }.getOrDefault(false)
        }?.absolutePath
    }

    private fun ManagedDocument.toBackupJson() = JSONObject().apply {
        put("documentId", documentId); put("propertyId", propertyId); putNullable("unitId", unitId)
        putNullable("receiptInternalId", receiptInternalId); put("documentType", documentType)
        put("documentCategory", documentCategory); put("documentDate", documentDate); put("title", title)
        put("originalFilename", originalFilename); put("storedFilename", storedFilename); put("mimeType", mimeType)
        putNullable("driveFileId", driveFileId); putNullable("driveFolderId", driveFolderId); put("sha256", sha256)
        put("fileSizeBytes", fileSizeBytes); put("createdAt", createdAt); put("updatedAt", updatedAt)
        put("ocrStatus", if (ocrText.isBlank()) ocrStatus else DocumentProcessingStatus.AUSSTEHEND.name)
        put("aiAnalysisStatus", aiAnalysisStatus); put("aiConfidence", aiConfidence); put("reviewStatus", reviewStatus)
        put("source", source); put("extractedFieldsJson", extractedFieldsJson); putNullable("loanId", loanId)
        putNullable("tenantReference", tenantReference); putNullable("renovationReference", renovationReference)
        put("migrationStatus", migrationStatus); putNullable("legacyDriveFolderId", legacyDriveFolderId)
    }

    private fun JSONObject.toManagedDocument() = ManagedDocument(
        documentId = optString("documentId"), propertyId = optString("propertyId", StableDocumentIdentity.LEGACY_PROPERTY_ID),
        unitId = nullableString("unitId"), receiptInternalId = nullableString("receiptInternalId"),
        documentType = optString("documentType", ManagedDocumentType.SONSTIGES.name),
        documentCategory = optString("documentCategory", "06_Sonstige_Objektunterlagen"),
        documentDate = optString("documentDate", ""), title = optString("title", ""),
        originalFilename = optString("originalFilename", ""), storedFilename = optString("storedFilename", ""),
        mimeType = optString("mimeType", "application/octet-stream"), localUri = "",
        driveFileId = nullableString("driveFileId"), driveFolderId = nullableString("driveFolderId"),
        sha256 = optString("sha256", ""), fileSizeBytes = optLong("fileSizeBytes", 0L),
        createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", ""),
        ocrStatus = DocumentProcessingStatus.AUSSTEHEND.name, ocrText = "",
        aiAnalysisStatus = optString("aiAnalysisStatus", DocumentProcessingStatus.AUSSTEHEND.name),
        aiConfidence = optDouble("aiConfidence", 0.0), reviewStatus = optString("reviewStatus", DocumentReviewStatus.PRUEFEN.name),
        source = optString("source", DocumentSource.DRIVE_RESTORE.name), extractedFieldsJson = optString("extractedFieldsJson", ""),
        loanId = nullableInt("loanId"), tenantReference = nullableString("tenantReference"),
        renovationReference = nullableString("renovationReference"), migrationStatus = optString("migrationStatus", ""),
        legacyDriveFolderId = nullableString("legacyDriveFolderId")
    )

    private fun JSONObject.toStandardRoute() = StandardRoute(
        id = optLong("id", 0L), name = optString("name", ""), startAddress = optString("startAddress", ""),
        destinationAddress = optString("destinationAddress", ""), stopsJson = optString("stopsJson", ""),
        routeMode = optString("routeMode", TripRouteMode.EINFACH.name), sameReturnRoute = optBoolean("sameReturnRoute", false),
        distanceKm = optDouble("distanceKm", 0.0), active = optBoolean("active", true),
        routeSignature = optString("routeSignature", ""), sourceProvider = optString("sourceProvider", ""),
        createdAt = optString("createdAt", ""), updatedAt = optString("updatedAt", "")
    )

    private fun JSONObject.putNullable(name: String, value: Any?) { put(name, value ?: JSONObject.NULL) }
    private fun JSONObject.nullableDouble(name: String): Double? = if (!has(name) || isNull(name)) null else optDouble(name)
    private fun JSONObject.nullableLong(name: String): Long? = if (!has(name) || isNull(name)) null else optLong(name)
    private fun JSONObject.nullableInt(name: String): Int? = if (!has(name) || isNull(name)) null else optInt(name)
    private fun JSONObject.nullableString(name: String): String? = if (!has(name) || isNull(name)) null else optString(name).takeIf(String::isNotBlank)

}
