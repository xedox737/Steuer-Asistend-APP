package com.example.data

import androidx.room.withTransaction
import com.example.util.ReceiptManifestService
import java.io.File
import java.util.UUID

/** Local repair for a verified pair of single-original receipts. Called under the maintenance gate. */
internal object ReceiptIdentityRepair {
    fun hash(file: File): String {
        require(file.isFile && file.length() in 1..(1024L * 1024 * 1024)) { "Original fehlt oder überschreitet die Dateigrenze." }
        return ReceiptManifestService.calculateSha256(file).also {
            require(it.matches(Regex("[0-9a-f]{64}"))) { "Original konnte nicht vollständig gelesen werden." }
        }
    }

    suspend fun apply(db: AppDatabase, keeper: Receipt, other: Receipt, first: File, second: File,
        firstHash: String, secondHash: String, remoteHashes: Map<String, String>, verifiedMainId: String) = db.withTransaction {
        val old = keeper.internalId
        require(old.isNotBlank() && keeper.id != other.id && other.internalId == old &&
            db.receiptDao().getRowsByInternalId(old).toSet() == setOf(keeper, other)) {
            "Die Beleggruppe hat sich verändert. Bitte erneut öffnen."
        }
        require(firstHash != secondHash && hash(first) == firstHash && hash(second) == secondHash) {
            "Für jeden Beleg wird sein eigenes, unverändertes Original benötigt."
        }
        require(listOf(keeper, other).all { it.deletionStatus == "ACTIVE" && ReceiptOriginalChain.paths(it.imageUrl).size <= 1 }) {
            "Archivierte oder mehrseitige Belege brauchen eine Einzelprüfung."
        }
        val sql = db.openHelper.writableDatabase
        val newId = "split-" + UUID.randomUUID()
        val docs = db.managedDocumentDao().getAllByReceiptId(old)
        val refs = db.receiptDocumentDao().getDocumentsForReceipt(old)
        fun owner(digest: String): Receipt = when (digest) {
            firstHash -> keeper
            secondHash -> other
            else -> error("Mindestens ein Anhang gehört zu einem weiteren Original. Beide Belege bleiben erhalten.")
        }
        require(shownBindingsMatchPair(keeper, other, remoteHashes, firstHash, secondHash)) {
            "Die gespeicherten Dateibindungen enthalten ein weiteres Original. Einzelprüfung erforderlich."
        }
        val documentOwners = docs.associate { doc ->
            val evidence = listOfNotNull(doc.sha256.takeIf(String::isNotBlank),
                doc.localUri.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)?.let(::hash),
                doc.driveFileId?.takeIf(String::isNotBlank)?.let { remoteHashes.getValue(it) }).distinct()
            val row = owner(evidence.singleOrNull() ?: error("Die Herkunft eines verknüpften Dokuments ist ungeklärt."))
            require(row.propertyId == doc.propertyId && (doc.unitId.isNullOrBlank() || row.unitId == doc.unitId)) {
                "Objekt oder Einheit des verknüpften Dokuments weicht ab."
            }
            doc.documentId to row.id
        }
        for (ref in refs) {
            val row = owner(remoteHashes[ref.driveFileId] ?: error("Für einen Anhang fehlt der Drive-Inhaltsnachweis."))
            require(documentOwners[ref.id]?.let { it == row.id } != false)
            if (row.id == other.id) {
                if (ref.role != ReceiptDocumentRole.MAIN_RECEIPT.name && ref.id !in documentOwners) {
                    require(db.managedDocumentDao().getById(ref.id) == null) { "Die Anhang-ID ist bereits vergeben." }
                    db.managedDocumentDao().upsert(ManagedDocument(ref.id, other.propertyId, receiptInternalId = newId,
                        unitId = other.unitId.takeIf(String::isNotBlank), localUri = second.absolutePath,
                        mimeType = ref.mimeType, originalFilename = ref.filename, storedFilename = second.name,
                        sha256 = secondHash, fileSizeBytes = second.length(), createdAt = ref.createdAt,
                        title = other.aussteller, documentDate = other.datum, source = DocumentSource.RECEIPT.name,
                        documentType = ManagedDocumentType.RECHNUNG.name))
                }
                db.receiptDocumentDao().insertDocument(ref.copy(receiptInternalId = newId, driveFileId = "", driveFolderId = null))
            }
        }
        for (doc in docs) {
            val moves = documentOwners.getValue(doc.documentId) == other.id
            require(!moves || doc.documentId != StableDocumentIdentity.receiptDocumentId(old)) {
                "Das Hauptdokument der bisherigen ID verweist auf das andere Original. Prüfung der Dokumenthistorie erforderlich."
            }
            val file = if (moves) second else first
            val updated = doc.copy(receiptInternalId = if (moves) newId else old, localUri = file.absolutePath,
                sha256 = if (moves) secondHash else firstHash, fileSizeBytes = file.length(),
                driveFileId = if (moves) null else doc.driveFileId, driveFolderId = if (moves) null else doc.driveFolderId)
            check(db.managedDocumentDao().updateExisting(updated) == 1) { "Dokument wurde zwischenzeitlich verändert." }
            if (moves) sql.execSQL("UPDATE document_migration_journal SET receiptInternalId=? WHERE receiptInternalId=? AND documentId=?",
                arrayOf(newId, old, doc.documentId))
        }
        require(db.managedDocumentDao().getMigrationJournal().none { it.receiptInternalId == old && it.documentId !in documentOwners }) {
            "Eine Dokumenthistorie besitzt keine eindeutige Zuordnung."
        }
        val rows = db.receiptDao().getAllReceiptsIncludingDeletedList()
        var display = "BLG-${other.datum.take(4)}-${other.id.toString().padStart(6, '0')}"
        if (rows.any { it.id != other.id && it.displayId == display }) display += "-${newId.removePrefix("split-")}"
        require(remoteHashes[verifiedMainId] == firstHash) { "Das erste Original passt nicht zur Drive-Zuordnung." }
        other.driveFileId?.takeIf { it.isNotBlank() && it != verifiedMainId }?.let {
            require(remoteHashes[it] == secondHash) { "Das zweite Original passt nicht zur gespeicherten Dateibindung." }
        }
        val unknownLinks = sql.query("SELECT 1 FROM bank_receipt_links WHERE receiptInternalId=? AND receiptId NOT IN (?,?) LIMIT 1",
            arrayOf<Any>(old, keeper.id, other.id)).use { it.moveToFirst() }
        require(!unknownLinks) { "Eine Bankverknüpfung besitzt keine eindeutige Datensatznummer." }
        sql.execSQL("UPDATE bank_receipt_links SET receiptInternalId=? WHERE receiptId=? AND receiptInternalId=?",
            arrayOf(newId, other.id, old))
        sql.execSQL("""UPDATE receipts SET internalId=?,displayId=?,imageUrl=?,fileSizeBytes=?,driveFileId=NULL,
            driveMetadataFileId=NULL,driveFolderId=NULL,driveRevision=NULL,isArchivedToDrive=0,lastSyncedAt=NULL,
            syncStatus='PENDING',syncError=NULL WHERE id=?""", arrayOf(newId, display, second.absolutePath, second.length(), other.id))
        sql.execSQL("UPDATE receipts SET imageUrl=?,driveFileId=? WHERE id=?", arrayOf(first.absolutePath, verifiedMainId, keeper.id))
        for ((row, file) in listOf(keeper to first, other to second)) {
            sql.execSQL("UPDATE receipt_entities SET pdfPath=? WHERE id=?", arrayOf(file.absolutePath, row.id))
            sql.execSQL("UPDATE belege SET bildPfad=? WHERE id=?", arrayOf(file.absolutePath, row.id))
            if (documentOwners.values.none { it == row.id }) {
                val stable = if (row.id == keeper.id) old else newId
                val docId = StableDocumentIdentity.receiptDocumentId(stable)
                require(db.managedDocumentDao().getById(docId) == null)
                db.managedDocumentDao().upsert(ManagedDocument(docId, row.propertyId, unitId = row.unitId.takeIf(String::isNotBlank),
                    receiptInternalId = stable, localUri = file.absolutePath, originalFilename = row.storedFilename ?: file.name,
                    storedFilename = file.name, mimeType = row.originalMimeType ?: "application/octet-stream",
                    fileSizeBytes = file.length(), sha256 = if (row.id == keeper.id) firstHash else secondHash,
                    driveFileId = if (row.id == keeper.id) verifiedMainId else null,
                    title = row.aussteller, documentDate = row.datum, source = DocumentSource.RECEIPT.name,
                    documentType = ManagedDocumentType.RECHNUNG.name))
            }
        }
        for (id in listOf(keeper.id, other.id)) {
            val receipt = requireNotNull(db.receiptDao().getReceiptById(id))
            for (document in db.managedDocumentDao().getAllByReceiptId(receipt.internalId)) {
                db.managedDocumentDao().deleteSearchEntry(document.documentId)
                db.managedDocumentDao().insertSearchEntry(DocumentSearchFts(document.documentId,
                    DocumentSearchTextBuilder.build(document, receipt)))
            }
        }
    }
    private fun shownBindingsMatchPair(keeper: Receipt, other: Receipt, hashes: Map<String, String>,
        first: String, second: String): Boolean = listOfNotNull(keeper.driveFileId, other.driveFileId)
        .filter(String::isNotBlank).all { hashes[it] in setOf(first, second) }
}
