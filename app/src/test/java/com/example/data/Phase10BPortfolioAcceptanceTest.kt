package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** T evidence only: real repositories/coordinator, in-memory transport; no live Drive or UI claim. */
@RunWith(RobolectricTestRunner::class)
class Phase10BPortfolioAcceptanceTest {
    @get:Rule val isolation = IsolatedAppDatabaseRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val db get() = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
    private lateinit var repository: ReceiptRepository
    private lateinit var drive: DrivePersistenceRepository
    private lateinit var fake: ManagedDocumentFakeDrive
    private lateinit var output: File
    private val timestamp = "2026-10-01T12:00:00Z"
    private val config = DriveAppConfig(rootFolderId = "root", systemFolderId = "system", receiptsFolderId = "receipts",
        paymentsFolderId = "payments", exportsFolderId = "exports", transactionsFolderId = "transactions",
        backupsFolderId = "backups", createdAt = timestamp, updatedAt = timestamp)

    @Before fun setup() {
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        repository = ReceiptRepository(db.receiptDao(), db.propertyDao(), db.receiptEntityDao(), db.belegDao(),
            db.exportAuditDao(), db.receiptDocumentDao(), db.managedDocumentDao())
        fake = ManagedDocumentFakeDrive()
        drive = DrivePersistenceRepository(context, repository, fake)
    }

    @After fun cleanup() {
        if (::repository.isInitialized) runBlocking { repository.getAllManagedDocuments().forEach { File(it.localUri).delete() } }
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
    }

    @Test fun seventeenUnitPortfolioEmptyTargetMergeTwiceAndReplace() = runTest { exercise(0) }
    @Test fun twentyOneUnitStressPortfolioEmptyTargetMergeTwiceAndReplace() = runTest { exercise(4) }

    private suspend fun exercise(extraUnits: Int) {
        output = File("build/reports/phase10b/units-${17 + extraUnits}").apply { mkdirs() }
        val failures = mutableListOf<String>()
        fun check(label: String, action: () -> Unit) {
            try { action() } catch (failure: AssertionError) { failures += "$label: ${failure.message}" }
        }
        val counts = listOf(8, 6 + extraUnits, 1, 1, 1)
        val names = listOf("Mehrfamilienhaus A", "Mehrfamilienhaus B", "ETW vermietet", "ETW Renovierung / leer", "Gewerbe Test")
        val properties = counts.mapIndexed { index, count ->
            val pid = "e10b-property-${index + 1}"
            val entries = (1..count).map { n -> UnitIdentity.Entry("$pid-unit-$n", when (n) {
                1 -> "WE 01, EG links"; 2 -> "WE 02"; 3 -> "WE 03; rechts"; 4, 5 -> "Gleicher Name"; else -> "WE $n"
            }) }
            val property = PropertyMetadata(id = index + 1, propertyId = pid, name = names[index], adresse = "Teststraße ${index + 1}",
                baujahr = 1954, gesamtKaufpreis = 200_000.0, gebaeudewert = 200_000.0,
                uebergangNutzenLasten = "2020-01-01", notariellesKaufdatum = "2019-12-10",
                objektart = if (index == 4) "Gewerbe" else if (index > 1) "Eigentumswohnung" else "Mehrfamilienhaus",
                wohneinheiten = UnitIdentity.encode(entries))
            repository.updatePropertyMetadata(property)
            val prefs = context.getSharedPreferences("wohneinheiten_prefs", 0).edit()
            entries.forEachIndexed { u, entry ->
                val prefix = "property_${pid}_unitid_${entry.unitId}_"
                val vacant = index == 3
                prefs.putFloat(prefix + "area", if (index == 2) 68f else when (u) { 0 -> 41.25f; 1 -> 55.5f; else -> 60f + u })
                    .putFloat(prefix + "rent", if (vacant) 0f else if (u < 2) 980f else 500f + u * 25)
                    .putString(prefix + "status", if (vacant) "Leerstand" else "Vermietet")
                    .putString(prefix + "label", entry.name).putString(prefix + "mieter", if (vacant) "" else "Testmieter $pid/$u")
                    .putString(prefix + "start", if (vacant) "" else "2010-01-01")
                TenantHistoryStore.save(context, pid, entry.unitId, entry.name, listOf(
                    TenantPeriod((index * 100 + u + 1).toLong(), entry.name, "Testmieter $pid/$u", "2010-01-01",
                        if (vacant) "2025-12-31" else "", if (u < 2) 980.0 else 500.0 + u * 25, 100.0 + u * 5, 0.0,
                        rentChanges = if (u == 2) listOf(RentAmountChange("2024-01-01", 600.0, 140.0, 0.0)) else emptyList())))
            }
            prefs.commit()
            property
        }
        assertEquals(17 + extraUnits, properties.sumOf { drive.getWohneinheitenFromPrefs(it).size })
        db.loanDao().upsertLoan(Loan(id = 71, bezeichnung = "MFH B Testdarlehen", bank = "Testbank", darlehensbetrag = 180_000.0,
            restschuld = 160_000.0, sollzinsProzent = 3.5, tilgungProzent = 2.0, monatlicheRate = 825.0,
            startDatum = "2020-01-01", zinsbindungBis = "2030-01-01", laufzeitBis = "2050-01-01", propertyId = properties[1].propertyId))
        ConfirmedAfaValuesStore.write(context, properties[1].propertyId, ConfirmedAfaValues("2026-06-30", 25_000.0, 184_000.0, "Synthetischer Stand"))
        val renovation = RenovationReviewStore(context)
        renovation.saveMeasure(RenovationMeasure("e10b-measure", properties[1].propertyId, "", "Sanierung Bad"))
        val fixtureBytes = SyntheticDocumentFixtureFactory.create().first().bytes
        val originals = (1..60).map { n ->
            val p = properties[(n - 1) % 5]
            val unit = drive.getWohneinheitenFromPrefs(p).first()
            val file = File(context.filesDir, "e10b-original-$n.pdf").apply { writeBytes(fixtureBytes + "\n% synthetisch $n".toByteArray()) }
            val r = Receipt(id = n, internalId = "e10b-receipt-$n", displayId = "E10B-$n", aussteller = "Testhandwerk ÄÖÜ $n",
                datum = "2026-01-15", uhrzeit = "10:15", bruttobetrag = when(n) { 2 -> 10_000.0; 7 -> -1_000.0; else -> n + .25 },
                hauptkategorie = if (n == 2 || n == 7) "Anschaffungskosten" else "Sonstige Ausgaben", unterkategorie = "Material",
                kontoNr = "4800", beschreibung = if (n == 7) "Gutschrift" else "Testbeleg $n", propertyId = p.propertyId,
                unitId = unit.unitId, wohneinheit = unit.name, imageUrl = file.absolutePath, driveFileId = "e10b-file-$n",
                driveMetadataFileId = "meta-e10b-receipt-$n", originalMimeType = "application/pdf",
                freigabestatus = if (n % 2 == 0) "FREIGEGEBEN" else "OFFEN")
            repository.insert(r)
            val doc = ManagedDocument(documentId = "receipt:${r.internalId}", propertyId = p.propertyId, unitId = unit.unitId,
                receiptInternalId = r.internalId, originalFilename = "Beleg, $n.pdf", storedFilename = "Beleg, $n.pdf",
                mimeType = "application/pdf", localUri = file.absolutePath, driveFileId = r.driveFileId,
                sha256 = StableDocumentIdentity.sha256(file.readBytes()), fileSizeBytes = file.length())
            repository.upsertManagedDocument(doc)
            fake.put(r.driveFileId!!, "receipts", doc.storedFilename, file.readBytes())
            doc
        }
        renovation.saveRelation(RenovationReceiptRelation("e10b-receipt-2", properties[1].propertyId, "e10b-measure"))
        val store = LogbookBookingStore(db, repository)
        repeat(10) { index ->
            val source = repository.getReceiptById(index + 1)!!
            store.saveLogbookTrip(source, "Besichtigung $index", "Teststart $index", "Testziel $index",
                listOf(TripStop("Teststart $index", "Start", 0), TripStop("Testziel $index", "Ziel", 1)), TripRouteMode.EINFACH, false,
                DistanceEvidence(manualKm = 20.0 + index, manuallyConfirmed = true), tripDate = "2026-09-${10 + index}",
                propertyId = source.propertyId, bookingKey = "e10b-trip-$index").getOrThrow()
        }
        store.correct(db.logbookDao().getAllTrips().first().id, "2026-09-10", "Korrektur Besichtigung", 22.0, "Tacho geprüft").getOrThrow()
        repeat(100) { db.exportAuditDao().insertRun(ExportAuditRun("e10b-audit-$it", timestamp = it.toLong(), status = "SUCCESS")) }
        val csv = "Buchungstag;Wertstellung;Betrag;Währung;Auftraggeber;Verwendungszweck;Referenz;Konto IBAN\n" +
            (1..120).joinToString("\n") { "01.10.2026;02.10.2026;$it,00;EUR;Testpartner $it;Miete;E10B-$it;DE02120300000000202051" } +
            "\n30.02.2026;;NaN;EUR;Fehler;Fehler;BAD;DE02120300000000202051"
        File(output, "bank-120.csv").writeText(csv)
        suspend fun importRows(name: String): Int {
            val parsed = BankImportParser.parseCsv(csv, name, timestamp, name)
            assertEquals(1, parsed.errorRows)
            val resolved = BankImportAccountResolver.resolve(parsed, db.bankDao().getAllAccounts())
            db.bankDao().upsertAccount(resolved.account)
            return db.bankDao().insertTransactions(resolved.transactions).count { it != -1L }
        }
        assertEquals(120, importRows("Bank.csv")); assertEquals(0, importRows("Bank.csv")); assertEquals(0, importRows("Anderer Name.csv"))
        val transactions = db.bankDao().getAllTransactions()
        transactions.take(6).forEachIndexed { index, tx ->
            val p = properties[index % 5]; val u = drive.getWohneinheitenFromPrefs(p).first()
            db.bankRentAssignmentDao().upsert(BankRentAssignment("e10b-assignment-$index", tx.transactionId, p.propertyId, u.unitId,
                "2026-10", u.mieter, tx.amount, if (index == 5) BankSplitPaymentType.DEPOSIT else BankSplitPaymentType.RENT,
                createdAt = timestamp, updatedAt = timestamp))
        }
        db.bankDao().upsertLink(BankReceiptLink("e10b-link", transactions.first().transactionId, 1, "e10b-receipt-1", 1.0))
        fake.putJson("property", "system", "propertyMetadata", with(drive) { properties.first().toJson() })
        fake.putJson("units", "system", "units", with(drive) { drive.getWohneinheitenFromPrefs(properties.first()).toJson() })
        val index = JSONObject().put("entries", JSONArray())
        repository.getAllReceiptsList().forEach { r ->
            assertNotNull(drive.upsertReceiptMetadata("test-token", config, r))
            index.getJSONArray("entries").put(JSONObject().put("internalId", r.internalId).put("displayId", r.displayId)
                .put("metadataFileId", "meta-${r.internalId}").put("mainDriveFileId", r.driveFileId.orEmpty())
                .put("bruttobetragCent", ReceiptMoney.toCent(r.bruttobetrag)).put("syncStatus", "SYNCED"))
        }
        fake.putJson("index", "system", "receiptIndex", index.toString())
        val payload = SupplementalDriveBackup.createPayload(context, db)
        fake.putJson("supplemental", "system", "supplementalBackup", payload.toString())
        File(output, "supplemental-backup.json").writeText(payload.toString(2))
        File(output, "supplemental-backup.sha256").writeText(StableDocumentIdentity.sha256(payload.toString(2).toByteArray()))
        File(output, "receipt-index.json").writeText(index.toString(2))
        File(output, "hash-manifest.json").writeText(JSONArray(originals.map { JSONObject().put("documentId", it.documentId)
            .put("driveFileId", it.driveFileId).put("sha256", it.sha256).put("size", it.fileSizeBytes) }).toString(2))
        val backup = File(output, "qa-transport-snapshot.zip")
        ZipOutputStream(backup.outputStream()).use { zip ->
            fun entry(name: String, bytes: ByteArray) { zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() }
            entry("supplemental.json", payload.toString().toByteArray())
            entry("index.json", index.toString().toByteArray())
            entry("property.json", fake.json("property").toByteArray())
            entry("units.json", fake.json("units").toByteArray())
            repository.getAllReceiptsList().forEach { entry("metadata/${it.internalId}.json", fake.json("meta-${it.internalId}").toByteArray()) }
            originals.forEach { entry("originals/${it.driveFileId}.pdf", File(it.localUri).readBytes()) }
        }
        File(output, "qa-transport-snapshot.sha256").writeText(StableDocumentIdentity.sha256(backup.readBytes()))
        val validated = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(validated.errors.toString(), validated.errors.none { it.isBlocking })
        val before = snapshot("BEFORE")
        // Remove all test original bytes as well as Room/preferences: no local-file residue can satisfy the hash check.
        originals.forEach { assertTrue(File(it.localUri).delete()) }
        withContext(Dispatchers.IO) { db.clearAllTables() }
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        suspend fun restore(mode: RestoreMode) {
            val result = FullRestoreCoordinator.execute(coreRestore = { drive.executeFullDriveRestore("test-token", config, mode) },
                preflight = { SupplementalDriveBackup.validatePayload(JSONObject(fake.json("supplemental"))) },
                supplementalRestore = {
                    SupplementalDriveBackup.restorePayload(context, db, JSONObject(fake.json("supplemental")),
                        replaceManagedDocuments = mode == RestoreMode.REPLACE_FULL, restoreMode = mode)
                    SupplementalDriveBackup.Result(true, "ok")
                })
            assertTrue(result.message, result.isSuccess)
            repository.getAllReceiptsList().filter { !it.driveFileId.isNullOrBlank() }.forEach { drive.downloadReceiptOriginals("test-token", it) }
        }
        restore(RestoreMode.MERGE)
        check("empty MERGE") { assertEquals(before, snapshot("AFTER_RESTORE_1")) }
        restore(RestoreMode.MERGE)
        check("second MERGE") { assertEquals(before, snapshot("AFTER_RESTORE_2")) }
        check("original bytes") {
            originals.forEach { source ->
                val restored = repository.getAllManagedDocuments().single { it.documentId == source.documentId }
                assertEquals(source.sha256, StableDocumentIdentity.sha256(File(restored.localUri).readBytes()))
            }
        }
        val newer = db.bankDao().getAllTransactions().first().copy(category = "Lokale Korrektur", updatedAt = "2026-10-17T12:00:00Z")
        db.bankDao().upsertTransaction(newer)
        db.bankDao().deleteLink("e10b-link")
        store.saveLogbookTrip(Receipt(aussteller = "Manuelle Fahrt", datum = "2026-10-17", uhrzeit = "11:00", bruttobetrag = 0.0,
            hauptkategorie = "", unterkategorie = "", kontoNr = "", beschreibung = "", propertyId = properties[0].propertyId),
            "Neue lokale Fahrt", "A", "B", listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
            DistanceEvidence(manualKm = 25.0, manuallyConfirmed = true), bookingKey = "e10b-new-local").getOrThrow()
        val local = snapshot("AFTER_LOCAL_CHANGES")
        restore(RestoreMode.MERGE)
        check("MERGE preserves local") { assertEquals(local, snapshot("AFTER_LOCAL_MERGE_1")) }
        restore(RestoreMode.MERGE)
        check("second local MERGE") { assertEquals(local, snapshot("AFTER_LOCAL_MERGE_2")) }
        restore(RestoreMode.REPLACE_FULL)
        check("REPLACE_FULL returns golden") { assertEquals(before, snapshot("AFTER_REPLACE_FULL")) }
        File(output, "assertion-results.json").writeText(JSONObject().put("failures", JSONArray(failures)).toString(2))
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private suspend fun snapshot(label: String): String {
        val result = JSONObject().put("supplemental", SupplementalDriveBackup.createPayload(context, db))
        result.put("units", JSONArray().apply { repository.getAllProperties().forEach { p ->
            put(JSONObject().put("propertyId", p.propertyId).put("units", JSONObject(with(drive) { getWohneinheitenFromPrefs(p).toJson() }).getJSONArray("units")))
        } })
        result.put("receipts", JSONArray().apply { repository.getAllReceiptsList().forEach { r ->
            put(JSONObject().put("id", r.id).put("internalId", r.internalId).put("displayId", r.displayId)
                .put("propertyId", r.propertyId).put("unitId", r.unitId).put("name", r.wohneinheit).put("tenant", r.mieter)
                .put("cent", ReceiptMoney.toCent(r.bruttobetrag)).put("date", r.datum).put("time", r.uhrzeit)
                .put("category", r.hauptkategorie).put("subcategory", r.unterkategorie).put("account", r.kontoNr)
                .put("description", r.beschreibung).put("approval", r.freigabestatus).put("exportId", r.exportlaufId)
                .put("allocations", r.allocationsJson).put("proposals", r.bookingProposalsJson).put("driveFileId", r.driveFileId))
        } })
        val text = canonical(result)
        File(output, "$label.json").writeText(text)
        return text
    }

    /** Compare every included value exactly. Entity arrays are sets; no numeric epsilon or field filtering. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { canonical(value.get(it)) }.sorted().joinToString(",", "[", "]")
        is String -> JSONObject.quote(value)
        is Number -> JSONObject.numberToString(value)
        null, JSONObject.NULL -> "null"
        else -> value.toString()
    }
}
