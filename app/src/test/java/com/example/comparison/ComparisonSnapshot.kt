package com.example.data

import android.content.Context
import com.example.ui.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Read-only, persisted device evidence. The existing Phase10D contract is shared unchanged. */
internal object ComparisonSnapshot {
    suspend fun capture(context: Context, db: AppDatabase, meta: JSONObject): JSONObject {
        val repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        val drive = DrivePersistenceRepository(context, repository)
        drive.getLatestRestoreJournal()?.let { journal ->
            // Allowlisted diagnostics only; error strings can contain domain/remote details.
            meta.put("restoreJournal", JSONObject().put("restoreId", journal.restoreId)
                .put("startedAt", journal.startedAt).put("completedAt", journal.completedAt)
                .put("phase", journal.phase).put("mode", journal.mode)
                .put("snapshotReceiptCount", journal.snapshotReceiptCount).put("importedReceiptCount", journal.importedReceiptCount))
        }
        val rawMoshi = com.squareup.moshi.Moshi.Builder()
            .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
        val raw = JSONObject().put("supplemental", SupplementalDriveBackup.createPayload(context, db))
        raw.put("units", JSONArray().apply { repository.getAllProperties().forEach { p ->
            put(JSONObject().put("propertyId", p.propertyId).put("units", JSONObject(with(drive) { getWohneinheitenFromPrefs(p).toJson() }).getJSONArray("units")))
        } })
        raw.put("receipts", JSONArray().apply { repository.getAllReceiptsList().forEach { r ->
            put(JSONObject().put("id", r.id).put("internalId", r.internalId).put("displayId", r.displayId)
                .put("propertyId", r.propertyId).put("unitId", r.unitId).put("name", r.wohneinheit).put("tenant", r.mieter)
                .put("cent", ReceiptMoney.toCent(r.bruttobetrag)).put("date", r.datum).put("time", r.uhrzeit)
                .put("category", r.hauptkategorie).put("subcategory", r.unterkategorie).put("account", r.kontoNr)
                .put("description", r.beschreibung).put("approval", r.freigabestatus).put("exportId", r.exportlaufId)
                .put("allocations", r.allocationsJson).put("proposals", r.bookingProposalsJson).put("driveFileId", r.driveFileId))
        } })
        val allReceipts = repository.getAllReceiptsList()
        raw.put("afa", JSONArray().apply { repository.getAllProperties().forEach { p ->
            val tax = TaxPropertyCalculator.calculate(p, allReceipts.filter { it.propertyId == p.propertyId })
            val confirmed = ConfirmedAfaValuesStore.read(context, p.propertyId)
            val forecast = afaForecast(tax, confirmed)
            val yearEnd = confirmed?.let { afaForecast(tax, it.copy(cutoffDate = "2026-12-31")) }.orEmpty()
            put(JSONObject().put("propertyId", p.propertyId).put("basis", tax.buildingAcquisitionCosts)
                .put("annualAfa", tax.annualAfa).put("limit15Percent", tax.limit15Percent)
                .put("forecastFirstYear", forecast.firstOrNull()?.year).put("forecastFirstMonths", forecast.firstOrNull()?.months)
                .put("forecastFirstAmount", forecast.firstOrNull()?.amount).put("yearEndFirstYear", yearEnd.firstOrNull()?.year))

        } })
        // Additional complete persisted Room rows retain device paths, original-chain
        // fields and OCR text too. No secret preferences or credentials are exported.
        val roomRaw = JSONObject().put("receipts", JSONArray().apply {
            val adapter = rawMoshi.adapter(Receipt::class.java)
            repository.getAllReceiptsIncludingDeletedList().forEach { put(JSONObject(adapter.toJson(it))) }
        }).put("managedDocuments", JSONArray().apply {
            val adapter = rawMoshi.adapter(ManagedDocument::class.java)
            repository.getAllManagedDocuments().forEach { put(JSONObject(adapter.toJson(it))) }
        })
        val chainEvidence = JSONObject()
        val currentDocuments = repository.getAllManagedDocuments()
        repository.getAllReceiptsIncludingDeletedList().forEach { receipt ->
            chainEvidence.put(receipt.internalId, JSONObject().apply {
                ReceiptOriginalChain.paths(receipt.imageUrl).forEachIndexed { rank, path ->
                    val matching = currentDocuments.singleOrNull {
                        it.receiptInternalId == receipt.internalId && it.localUri.removePrefix("file://") == path.removePrefix("file://")
                    }
                    val file = File(path.removePrefix("file://"))
                    put(rank.toString(), JSONObject().put("documentId", matching?.documentId ?: "<unresolved>")
                        .put("readable", file.isFile).put("sha256", if (file.isFile) StableDocumentIdentity.sha256(file.readBytes()) else "<missing>"))
                }
            })
        }
        roomRaw.put("receiptOriginalChains", chainEvidence)
        val originals = JSONArray()
        currentDocuments.filter { it.sha256.isNotBlank() && it.fileSizeBytes > 0 }.forEach { doc ->
            val file = File(doc.localUri.removePrefix("file://"))
            val readable = file.isFile && file.canRead()
            originals.put(JSONObject().put("documentId", doc.documentId).put("receiptInternalId", doc.receiptInternalId)
                .put("sha256", if (readable) StableDocumentIdentity.sha256(file.readBytes()) else "<missing>")
                .put("expectedSha256", doc.sha256).put("size", file.length()).put("expectedSize", doc.fileSizeBytes)
                .put("mime", doc.mimeType).put("filename", doc.originalFilename).put("readable", readable))
        }
        val tenants = JSONArray()
        raw.getJSONArray("units").let { properties ->
            for (i in 0 until properties.length()) {
                val property = properties.getJSONObject(i)
                val units = property.getJSONArray("units")
                for (j in 0 until units.length()) {
                    val unit = units.getJSONObject(j)
                    val pid = property.getString("propertyId")
                    val uid = unit.getString("unitId")
                    val key = "history_v2_${pid}_$uid"
                    val stored = raw.getJSONObject("supplemental").getJSONObject("tenantHistoryPrefs").optJSONObject(key)
                    val history = stored?.getString("value")?.let(::JSONArray) ?: JSONArray()
                    for (k in 0 until history.length()) tenants.put(JSONObject(history.getJSONObject(k).toString())
                        .put("propertyId", pid).put("unitId", uid))
                }
            }
        }
        val secondaryRoom = JSONObject().put("belege", JSONArray().apply {
            val adapter = rawMoshi.adapter(Beleg::class.java)
            db.belegDao().getAllBelege().first().forEach { put(JSONObject(adapter.toJson(it))) }
        }).put("receiptEntities", JSONArray().apply {
            val adapter = rawMoshi.adapter(ReceiptEntity::class.java)
            db.receiptEntityDao().getAllEntities().first().forEach { put(JSONObject(adapter.toJson(it))) }
        })
        val counts = JSONObject()
        val supplemental = raw.getJSONObject("supplemental")
        supplemental.keys().forEach { key ->
            (supplemental.get(key) as? JSONArray)?.let { counts.put(key, it.length()) }
        }
        counts.put("receipts", raw.getJSONArray("receipts").length())
            .put("roomReceipts", roomRaw.getJSONArray("receipts").length())
            .put("roomDocuments", roomRaw.getJSONArray("managedDocuments").length())
            .put("units", (0 until raw.getJSONArray("units").length()).sumOf {
                raw.getJSONArray("units").getJSONObject(it).getJSONArray("units").length() })
            .put("afa", raw.getJSONArray("afa").length()).put("tenancies", tenants.length())
            .put("originals", originals.length()).put("belege", secondaryRoom.getJSONArray("belege").length())
            .put("receiptEntities", secondaryRoom.getJSONArray("receiptEntities").length())
        return JSONObject().put("counts", counts).put("secondaryRoom", secondaryRoom).put("schemaVersion", 1).put("meta", meta).put("raw", raw).put("roomRaw", roomRaw)
            .put("contract", JSONObject(RestoreContractSnapshot.fromRaw(canonicalJson(raw), canonicalJson(roomRaw)).canonical()))
            .put("tenancies", tenants).put("originals", originals)
    }
}
