package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    private val captured = linkedMapOf<String, String>()
    private val capturedRoom = linkedMapOf<String, String>()
    private val rawMoshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build()
    private val comparisonSummary = JSONObject()
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




    @Test fun largeDatevBatchHasBomCrlfValidFormatAndAllOriginalHashesInActualProductZip() {
        output = File("build/reports/phase10b/datev").apply { mkdirs() }
        val formats = formatBytes().entries.toList()
        val receipts = (1..120).map { n ->
            val format = formats[(n - 1) % 5]
            val file = File(context.cacheDir, "e10b-export-$n.${format.key}").apply { writeBytes(format.value + "\n% e10b-export-$n".toByteArray()) }
            Receipt(id = n, internalId = "export-receipt-$n", displayId = "E10B-$n", aussteller = "Handwerk ÄÖÜ",
                datum = "2026-10-01", uhrzeit = "12:00", bruttobetrag = 10.25, hauptkategorie = "Renovierung", unterkategorie = "Reparatur",
                kontoNr = "4800", beschreibung = "Prüfung; \"Fenster\"", propertyId = "e10b-property-1", imageUrl = file.absolutePath,
                originalMimeType = mime(format.key), freigabestatus = "FREIGEGEBEN")
        }
        val records = receipts.map { r -> BookingRecord(bookingId = "booking-${r.id}", receiptId = r.id,
            belegnummer = r.displayId!!, belegdatum = r.datum, buchungsdatum = r.datum, zahlungspartner = r.aussteller,
            beschreibung = r.beschreibung, bruttobetrag = r.bruttobetrag, sollHaben = "S", sachkonto = "4800", gegenkonto = "1200",
            belegfeld1 = r.displayId!!, hauptkategorie = "Renovierung", objektId = r.propertyId, kost1 = r.propertyId, steuerlichesJahr = 2026) }
        val profile = DatevProfile.createDefaultSkr03().copy(beraterNummer = "1111111", mandantenNummer = "11111")
        val validation = com.example.util.BookingValidationService.validateRecords(records, profile)
        val csv = com.example.util.DatevCsvOutput.create(context, records, profile, validation, "2026")
        assertEquals("text/csv", csv.mimeType)
        assertEquals("csv", csv.outputFile.extension)
        val bytes = csv.outputFile.readBytes()
        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.take(3).toByteArray())
        val text = bytes.drop(3).toByteArray().toString(Charsets.UTF_8)
        assertTrue(com.example.util.DatevFormatValidator.validate(text).isValid)
        assertEquals(122, text.trimEnd().split("\r\n").size)
        assertFalse(text.replace("\r\n", "").contains("\n"))
        csv.outputFile.copyTo(File(output, "batch-120.csv"), overwrite = true)
        val pkg = com.example.util.AdvisorPackageBuilder.buildPackage(context, records, receipts, emptyList(), true, profile, validation)
        assertEquals("application/zip", pkg.mimeType)
        java.util.zip.ZipFile(pkg.outputFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val originals = entries.filter { !it.isDirectory && it.name.startsWith("02_Originalbelege/") }
            assertEquals(120, originals.size)
            assertEquals(entries.size, entries.map { it.name }.distinct().size)
            assertTrue(entries.any { it.name.startsWith("01_DATEV/") && it.name.endsWith(".csv") })
            assertNotNull(zip.getEntry("08_Pruefprotokoll/manifest.json"))
            receipts.forEach { r ->
                val entry = originals.single { it.name.contains("_${r.displayId}_Original.") }
                assertEquals(StableDocumentIdentity.sha256(File(r.imageUrl).readBytes()),
                    StableDocumentIdentity.sha256(zip.getInputStream(entry).use { it.readBytes() }))
            }
        }
        pkg.outputFile.copyTo(File(output, "batch-120.zip"), overwrite = true)
        File(output, "export-hashes.json").writeText(JSONObject().put("csv", StableDocumentIdentity.sha256(bytes))
            .put("zip", StableDocumentIdentity.sha256(pkg.outputFile.readBytes())).toString(2))
    }

    @Test fun actualImportServiceReadsAppFileProviderAndRestoresFiveFormatMetadataTwice() = runTest {
        output = File("build/reports/phase10b/file-formats").apply { mkdirs() }
        val property = PropertyMetadata(id = 1, propertyId = "e10b-formats", name = "Dateitest")
        repository.updatePropertyMetadata(property)
        val service = ManagedDocumentService(context, repository)
        val imports = formatBytes().map { (extension, bytes) ->
            val source = File(context.cacheDir, "e10b-source.$extension").apply { writeBytes(bytes) }
            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".provider", source)
            val result = service.prepareImport(uri, property, "e10b-unit")
            assertTrue(result.toString(), result is ManagedDocumentImportResult.Imported)
            val doc = (result as ManagedDocumentImportResult.Imported).document
            assertEquals(mime(extension), doc.mimeType)
            assertEquals(source.name, doc.originalFilename)
            assertEquals(StableDocumentIdentity.sha256(bytes), doc.sha256)
            assertArrayEquals(bytes, File(doc.localUri).readBytes())
            doc
        }
        val payload = SupplementalDriveBackup.createPayload(context, db)
        File(output, "BEFORE.json").writeText(canonical(payload))
        // This case tests metadata roundtrip with existing local originals; empty-target original
        // download is independently checked by the portfolio cases above.
        db.managedDocumentDao().deleteAllDocuments()
        repeat(2) { n ->
            SupplementalDriveBackup.restorePayload(context, db, payload)
            val after = SupplementalDriveBackup.createPayload(context, db)
            File(output, "AFTER_${n + 1}.json").writeText(canonical(after))
            assertEquals(canonical(payload), canonical(after))
            imports.forEach { before ->
                val doc = repository.getManagedDocument(before.documentId)!!
                assertEquals(before.mimeType, doc.mimeType)
                assertEquals(before.originalFilename, doc.originalFilename)
                assertEquals(before.unitId, doc.unitId)
                assertEquals(before.sha256, StableDocumentIdentity.sha256(File(doc.localUri).readBytes()))
            }
        }
        assertTrue(service.prepareImport(android.net.Uri.parse("content://missing-e10b-provider/invalid"), property) is ManagedDocumentImportResult.Error)
    }

    private fun mime(extension: String) = when (extension) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; else -> "image/$extension"
    }
    // Valid synthetic fixtures, generated with Pillow/reportlab; no user data.
    private fun formatBytes(): Map<String, ByteArray> = linkedMapOf(
        "pdf" to java.util.Base64.getDecoder().decode("JVBERi0xLjMKJZOMi54gUmVwb3J0TGFiIEdlbmVyYXRlZCBQREYgZG9jdW1lbnQgKG9wZW5zb3VyY2UpCjEgMCBvYmoKPDwKL0YxIDIgMCBSCj4+CmVuZG9iagoyIDAgb2JqCjw8Ci9CYXNlRm9udCAvSGVsdmV0aWNhIC9FbmNvZGluZyAvV2luQW5zaUVuY29kaW5nIC9OYW1lIC9GMSAvU3VidHlwZSAvVHlwZTEgL1R5cGUgL0ZvbnQKPj4KZW5kb2JqCjMgMCBvYmoKPDwKL0NvbnRlbnRzIDcgMCBSIC9NZWRpYUJveCBbIDAgMCA1OTUuMjc1NiA4NDEuODg5OCBdIC9QYXJlbnQgNiAwIFIgL1Jlc291cmNlcyA8PAovRm9udCAxIDAgUiAvUHJvY1NldCBbIC9QREYgL1RleHQgL0ltYWdlQiAvSW1hZ2VDIC9JbWFnZUkgXQo+PiAvUm90YXRlIDAgL1RyYW5zIDw8Cgo+PiAKICAvVHlwZSAvUGFnZQo+PgplbmRvYmoKNCAwIG9iago8PAovUGFnZU1vZGUgL1VzZU5vbmUgL1BhZ2VzIDYgMCBSIC9UeXBlIC9DYXRhbG9nCj4+CmVuZG9iago1IDAgb2JqCjw8Ci9BdXRob3IgKGFub255bW91cykgL0NyZWF0aW9uRGF0ZSAoRDoyMDAwMDEwMTAwMDAwMCswMCcwMCcpIC9DcmVhdG9yIChhbm9ueW1vdXMpIC9LZXl3b3JkcyAoKSAvTW9kRGF0ZSAoRDoyMDAwMDEwMTAwMDAwMCswMCcwMCcpIC9Qcm9kdWNlciAoUmVwb3J0TGFiIFBERiBMaWJyYXJ5IC0gXChvcGVuc291cmNlXCkpIAogIC9TdWJqZWN0ICh1bnNwZWNpZmllZCkgL1RpdGxlICh1bnRpdGxlZCkgL1RyYXBwZWQgL0ZhbHNlCj4+CmVuZG9iago2IDAgb2JqCjw8Ci9Db3VudCAxIC9LaWRzIFsgMyAwIFIgXSAvVHlwZSAvUGFnZXMKPj4KZW5kb2JqCjcgMCBvYmoKPDwKL0ZpbHRlciBbIC9BU0NJSTg1RGVjb2RlIC9GbGF0ZURlY29kZSBdIC9MZW5ndGggMTI1Cj4+CnN0cmVhbQpHYXBRaDBFPUYsMFVcSDNUXHBOWVReUUtrP3RjPklQLDtXI1UxXjIzaWhQRU1fP0NXNEtJU2k6OmJXLjZPQl9zS0o7aEcrPCE+VmstTyFlMGlsXG9zTjc9JDdBOzByVWJrMCxsYCNNTCpPPVoocy9XYixaL2Ngbl4pLVt+PmVuZHN0cmVhbQplbmRvYmoKeHJlZgowIDgKMDAwMDAwMDAwMCA2NTUzNSBmIAowMDAwMDAwMDYxIDAwMDAwIG4gCjAwMDAwMDAwOTIgMDAwMDAgbiAKMDAwMDAwMDE5OSAwMDAwMCBuIAowMDAwMDAwNDAyIDAwMDAwIG4gCjAwMDAwMDA0NzAgMDAwMDAgbiAKMDAwMDAwMDczMSAwMDAwMCBuIAowMDAwMDAwNzkwIDAwMDAwIG4gCnRyYWlsZXIKPDwKL0lEIApbPDFjMTc4MTk4ZmJkZmE1MWIyNTk5NWQ4OWQ0MTAyMDQzPjwxYzE3ODE5OGZiZGZhNTFiMjU5OTVkODlkNDEwMjA0Mz5dCiUgUmVwb3J0TGFiIGdlbmVyYXRlZCBQREYgZG9jdW1lbnQgLS0gZGlnZXN0IChvcGVuc291cmNlKQoKL0luZm8gNSAwIFIKL1Jvb3QgNCAwIFIKL1NpemUgOAo+PgpzdGFydHhyZWYKMTAwNQolJUVPRgo="),
        "jpg" to java.util.Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAgACADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDhaKKK+oPnwooooAKKKKACiiigD//Z"),
        "jpeg" to java.util.Base64.getDecoder().decode("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAgACADASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDkaKKK+iOEKKKKACiiigAooooA/9k="),
        "png" to java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAIAAAD8GO2jAAAALUlEQVR4nGNMCZjGQEvARFPTRy0YtWDUglELRi0YtWDUglELRi0YtWDUAioCAC+ZAYopCJCbAAAAAElFTkSuQmCC"),
        "webp" to java.util.Base64.getDecoder().decode("UklGRkQAAABXRUJQVlA4IDgAAAAwAwCdASogACAAPm00lkekIyIhKAgAgA2JZQDJAoAAQFBQAP7paR/alEFJal/UREFJXAny3AAAAA==")
    )

    private suspend fun exercise(extraUnits: Int) {
        output = File("build/reports/phase10b/units-${17 + extraUnits}").apply { mkdirs() }
        val failures = mutableListOf<String>()
        suspend fun check(label: String, action: suspend () -> Unit) {
            try { action() } catch (failure: AssertionError) { failures += "$label: ${failure.message}" }
        }
        val counts = listOf(8, 6 + extraUnits, 1, 1, 1)
        val names = listOf("Mehrfamilienhaus A", "Mehrfamilienhaus B", "ETW vermietet", "ETW Renovierung / leer", "Gewerbe Test")
        val properties = counts.mapIndexed { index, count ->
            val pid = "e10b-property-${index + 1}"
            val entries = (1..count).map { n -> UnitIdentity.Entry("$pid-unit-$n", when (n) {
                1 -> "WE 01, EG links"; 2 -> "WE 02"; 3 -> "WE 03; rechts"; 4 -> "WE 04"; 5 -> "WE 05"; else -> "WE $n"
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
        val formats = formatBytes().entries.toList()
        val originals = (1..60).map { n ->
            val p = properties[(n - 1) % 5]
            val unit = drive.getWohneinheitenFromPrefs(p).first()
            val format = formats[(n - 1) % formats.size]
            val file = File(context.filesDir, "e10b-original-$n.${format.key}").apply { writeBytes(format.value + "\n% synthetic $n".toByteArray()) }
            val r = Receipt(id = n, internalId = "e10b-receipt-$n", displayId = "E10B-$n", aussteller = "Testhandwerk ÄÖÜ $n",
                datum = "2026-01-15", uhrzeit = "10:15", bruttobetrag = when(n) { 2 -> 10_000.0; 7 -> -1_000.0; else -> n + .25 },
                hauptkategorie = if (n == 2 || n == 7) "Anschaffungskosten" else "Sonstige Ausgaben", unterkategorie = "Material",
                kontoNr = "4800", beschreibung = if (n == 7) "Gutschrift" else "Testbeleg $n", propertyId = p.propertyId,
                unitId = unit.unitId, wohneinheit = unit.name, imageUrl = file.absolutePath, driveFileId = "e10b-file-$n",
                driveMetadataFileId = "meta-e10b-receipt-$n", originalMimeType = mime(format.key),
                freigabestatus = if (n % 2 == 0) "FREIGEGEBEN" else "OFFEN").let { receipt ->
                if (n != 4) receipt else {
                    val approved = com.example.util.DatevMappingService.confirmDatevPreview(receipt,
                        com.example.util.DatevMappingService.buildDatevBookingRows(receipt, DatevProfile()))!!
                    approved.copy(allocationsJson = " \n" + approved.allocationsJson,
                        bookingProposalsJson = " \n" + approved.bookingProposalsJson,
                        exportStatus = "EXPORTIERT", exportlaufId = "e10b-audit-0")
                }
            }
            repository.insert(r)
            val doc = ManagedDocument(documentId = "receipt:${r.internalId}", propertyId = p.propertyId, unitId = unit.unitId,
                receiptInternalId = r.internalId, originalFilename = "Beleg, $n.${format.key}", storedFilename = "Beleg, $n.${format.key}",
                mimeType = mime(format.key), localUri = file.absolutePath, driveFileId = r.driveFileId,
                sha256 = StableDocumentIdentity.sha256(file.readBytes()), fileSizeBytes = file.length())
            repository.upsertManagedDocument(doc)
            fake.put(r.driveFileId!!, "receipts", doc.storedFilename, file.readBytes())
            doc
        }
        val reviewService = ManagedDocumentService(context, repository)
        val reviewDocument = originals.first().copy(title = "Fachlicher Titel vor Backup")
        repository.upsertManagedDocument(reviewDocument)
        reviewService.confirmReview(reviewDocument, ManagedDocumentType.RECHNUNG, "2026-01-15", reviewDocument.unitId,
            JSONObject().put("_displayDescription", "Bestätigte Beschreibung vor Backup")
                .put("rechnungNummer", "E10B-REVIEW-BACKUP").toString())
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
        val importedRows = listOf(importRows("Bank.csv"), importRows("Bank.csv"), importRows("Anderer Name.csv"))
        File(output, "bank-import-results.json").writeText(JSONObject().put("inserted", JSONArray(importedRows))
            .put("errorRowsPerImport", 1).toString(2))
        assertEquals(listOf(120, 0, 0), importedRows)
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
                .put("bruttobetragCent", ReceiptMoney.toCent(r.bruttobetrag)).put("syncStatus", "SYNCED")
                .put("datum", r.datum).put("unterkategorie", r.unterkategorie).apply {
                    // The real writer now emits an explicit, bound proof for fileless trips.
                    // The synthetic index must carry the same proof as its actual metadata.
                    val metadata = JSONObject(fake.json("meta-${r.internalId}"))
                    if (metadata.has("filelessLogbookEvidence"))
                        put("filelessLogbookEvidence", metadata.getJSONObject("filelessLogbookEvidence"))
                })
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
            originals.forEach { entry("originals/${it.driveFileId}.${it.storedFilename.substringAfterLast('.')}", File(it.localUri).readBytes()) }
        }
        File(output, "qa-transport-snapshot.sha256").writeText(StableDocumentIdentity.sha256(backup.readBytes()))
        val validated = drive.buildRestoreSnapshot("test-token", config)
        assertTrue(validated.errors.toString(), validated.errors.none { it.isBlocking })
        val before = snapshot("BEFORE")
        // Remove all test original bytes as well as Room/preferences: no local-file residue can satisfy the hash check.
        originals.forEach { assertTrue(File(it.localUri).delete()) }
        withContext(Dispatchers.IO) { db.clearAllTables() }
        PersistentPreferenceInventory.stores.forEach { context.getSharedPreferences(it.name, 0).edit().clear().commit() }
        val emptyTarget = JSONObject().put("receipts", repository.getAllReceiptsList().size)
            .put("documents", repository.getAllManagedDocuments().size).put("properties", repository.getAllProperties().size)
            .put("trips", db.logbookDao().getAllTrips().size).put("bankTransactions", db.bankDao().getAllTransactions().size)
            .put("remainingOriginals", originals.count { File(it.localUri).exists() })
            .put("preferenceEntries", PersistentPreferenceInventory.stores.sumOf { context.getSharedPreferences(it.name, 0).all.size })
        File(output, "empty-target.json").writeText(emptyTarget.toString(2))
        emptyTarget.keys().forEach { assertEquals("Empty target: $it", 0, emptyTarget.getInt(it)) }
        suspend fun restore(mode: RestoreMode) {
            val documentsBeforeCore = repository.getAllManagedDocuments().associateBy { it.documentId }
            val result = FullRestoreCoordinator.execute(coreRestore = { drive.executeFullDriveRestore("test-token", config, mode) },
                preflight = { SupplementalDriveBackup.validatePayload(JSONObject(fake.json("supplemental"))) },
                supplementalRestore = {
                    SupplementalDriveBackup.restorePayload(context, db, JSONObject(fake.json("supplemental")),
                        replaceManagedDocuments = mode == RestoreMode.REPLACE_FULL, restoreMode = mode, localDocumentsBeforeCore = documentsBeforeCore)
                    SupplementalDriveBackup.Result(true, "ok")
                })
            assertTrue(result.message, result.isSuccess)
            repository.getAllReceiptsList().filter { !it.driveFileId.isNullOrBlank() }.forEach { drive.downloadReceiptOriginals("test-token", it) }
        }
        restore(RestoreMode.MERGE)
        check("empty MERGE") { assertTrue("Restore contract diff: AFTER_RESTORE_1", compare("BEFORE", "AFTER_RESTORE_1", before, snapshot("AFTER_RESTORE_1"))) }
        restore(RestoreMode.MERGE)
        check("second MERGE") { assertTrue("Restore contract diff: AFTER_RESTORE_2", compare("BEFORE", "AFTER_RESTORE_2", before, snapshot("AFTER_RESTORE_2"))) }
        check("repeat MERGE raw and contract") {
            assertTrue(compare("AFTER_RESTORE_1", "AFTER_RESTORE_2", captured.getValue("AFTER_RESTORE_1"),
                captured.getValue("AFTER_RESTORE_2"), exactRaw = true))
        }
        check("original bytes") {
            originals.forEach { source ->
                val restored = repository.getAllManagedDocuments().single { it.documentId == source.documentId }
                assertEquals(source.sha256, StableDocumentIdentity.sha256(File(restored.localUri).readBytes()))
            }
        }
        val newer = db.bankDao().getAllTransactions().first().copy(category = "Lokale Korrektur", updatedAt = "2026-10-17T12:00:00Z")
        db.bankDao().upsertTransaction(newer)
        db.bankDao().deleteLink("e10b-link")
        db.bankDao().upsertLink(BankReceiptLink("e10b-local-link", transactions.last().transactionId, 1, "e10b-receipt-1", 1.0))
        val restoredDocument = repository.getManagedDocument(reviewDocument.documentId)!!
        val reviewedLocal = reviewService.confirmReview(restoredDocument, ManagedDocumentType.RECHNUNG,
            restoredDocument.documentDate, restoredDocument.unitId,
            JSONObject(restoredDocument.extractedFieldsJson).put("rechnungNummer", "E10B-REVIEW-LOCAL").toString())
        repository.upsertManagedDocument(reviewedLocal.copy(title = "Neuerer lokaler fachlicher Titel"))
        store.saveLogbookTrip(Receipt(aussteller = "Manuelle Fahrt", datum = "2026-10-17", uhrzeit = "11:00", bruttobetrag = 0.0,
            hauptkategorie = "", unterkategorie = "", kontoNr = "", beschreibung = "", propertyId = properties[0].propertyId),
            "Neue lokale Fahrt", "A", "B", listOf(TripStop("A", "Start", 0), TripStop("B", "Ziel", 1)), TripRouteMode.EINFACH, false,
            DistanceEvidence(manualKm = 25.0, manuallyConfirmed = true), bookingKey = "e10b-new-local").getOrThrow()
        val local = snapshot("AFTER_LOCAL_CHANGES")
        restore(RestoreMode.MERGE)
        check("MERGE preserves local") { assertTrue("Restore contract diff: AFTER_LOCAL_MERGE_1", compare("AFTER_LOCAL_CHANGES", "AFTER_LOCAL_MERGE_1", local, snapshot("AFTER_LOCAL_MERGE_1"), exactRaw = true)) }
        restore(RestoreMode.MERGE)
        check("second local MERGE") { assertTrue("Restore contract diff: AFTER_LOCAL_MERGE_2", compare("AFTER_LOCAL_CHANGES", "AFTER_LOCAL_MERGE_2", local, snapshot("AFTER_LOCAL_MERGE_2"), exactRaw = true)) }
        check("repeat local MERGE raw and contract") {
            assertTrue(compare("AFTER_LOCAL_MERGE_1", "AFTER_LOCAL_MERGE_2", captured.getValue("AFTER_LOCAL_MERGE_1"),
                captured.getValue("AFTER_LOCAL_MERGE_2"), exactRaw = true))
        }
        restore(RestoreMode.REPLACE_FULL)
        check("REPLACE_FULL returns golden") { assertTrue("Restore contract diff: AFTER_REPLACE_FULL", compare("BEFORE", "AFTER_REPLACE_FULL", before, snapshot("AFTER_REPLACE_FULL"))) }
        val receiptIds = repository.getAllReceiptsList().map { it.id }.toSet()
        val orphans = db.logbookDao().getAllTrips().filter { it.expenseReceiptId !in receiptIds }
        File(output, "relation-integrity.json").writeText(JSONObject().put("orphanTrips", JSONArray(orphans.map {
            JSONObject().put("tripId", it.id).put("missingExpenseReceiptId", it.expenseReceiptId)
        })).toString(2))
        check("E10B-01 REPLACE_FULL orphan relation") { assertTrue("Trips reference missing expense receipts: $orphans", orphans.isEmpty()) }
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
        val allReceipts = repository.getAllReceiptsList()
        result.put("afa", JSONArray().apply { repository.getAllProperties().forEach { p ->
            val tax = TaxPropertyCalculator.calculate(p, allReceipts.filter { it.propertyId == p.propertyId })
            val confirmed = ConfirmedAfaValuesStore.read(context, p.propertyId)
            val forecast = afaForecast(tax, confirmed)
            val yearEnd = confirmed?.let { afaForecast(tax, it.copy(cutoffDate = "2026-12-31")) }.orEmpty()
            put(JSONObject().put("propertyId", p.propertyId).put("basis", tax.buildingAcquisitionCosts)
                .put("annualAfa", tax.annualAfa).put("limit15Percent", tax.limit15Percent)
                .put("forecastFirstYear", forecast.firstOrNull()?.year).put("forecastFirstMonths", forecast.firstOrNull()?.months)
                .put("forecastFirstAmount", forecast.firstOrNull()?.amount).put("yearEndFirstYear", yearEnd.firstOrNull()?.year))
            if (p.propertyId == "e10b-property-2") {
                assertEquals(209_000.0, tax.buildingAcquisitionCosts, 0.0)
                assertEquals(4_180.0, tax.annualAfa, 0.0)
                assertEquals(31_350.0, tax.limit15Percent, 0.0)
                assertEquals(6, forecast.first().months)
                assertEquals(2027, yearEnd.first().year)
            }
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
        capturedRoom[label] = canonical(roomRaw)
        File(output, "$label.ROOM-RAW.json").writeText(capturedRoom.getValue(label))
        val text = canonical(result)
        File(output, "$label.json").writeText(text)
        File(output, "$label.CONTRACT.json").writeText(RestoreContractSnapshot.fromRaw(text, capturedRoom.getValue(label)).canonical())
        recordIntegrity(label)
        captured[label] = text
        return text
    }

    /** Assert identities, physical originals and relations at every persisted checkpoint. */
    private suspend fun recordIntegrity(label: String) {
        val receipts = repository.getAllReceiptsList()
        val documents = repository.getAllManagedDocuments()
        val trips = db.logbookDao().getAllTrips()
        val properties = repository.getAllProperties()
        val transactions = db.bankDao().getAllTransactions()
        val accounts = db.bankDao().getAllAccounts()
        val links = db.bankDao().getAllLinks()
        val assignments = db.bankRentAssignmentDao().getAll()
        val receiptById = receipts.associateBy { it.id }
        val receiptByInternalId = receipts.associateBy { it.internalId }
        val propertyIds = properties.map { it.propertyId }.toSet()
        val unitIds = properties.flatMap { p -> drive.getWohneinheitenFromPrefs(p).map { p.propertyId to it.unitId } }.toSet()
        val problems = mutableListOf<String>()
        fun requireRelation(valid: Boolean, message: String) { if (!valid) problems += message }
        requireRelation(receipts.size == receiptById.size && receipts.size == receiptByInternalId.size, "duplicate receipt IDs")
        requireRelation(documents.size == documents.map { it.documentId }.distinct().size, "duplicate document IDs")
        receipts.forEach { r ->
            requireRelation(r.propertyId in propertyIds, "receipt ${r.id}: missing property")
            if (!r.unitId.isNullOrBlank()) requireRelation(r.propertyId to r.unitId in unitIds, "receipt ${r.id}: missing unit")
        }
        documents.forEach { d ->
            requireRelation(d.propertyId in propertyIds, "document ${d.documentId}: missing property")
            d.receiptInternalId?.let { id ->
                val receipt = receiptByInternalId[id]
                requireRelation(receipt != null && receipt.propertyId == d.propertyId && receipt.unitId.orEmpty() == d.unitId.orEmpty(),
                    "document ${d.documentId}: missing/mismatched receipt scope")
            }
        }
        trips.forEach { trip ->
            requireRelation(trip.expenseReceiptId in receiptById, "trip ${trip.id}: missing expense receipt")
            trip.sourceReceiptId?.let { requireRelation(it in receiptById, "trip ${trip.id}: missing source receipt") }
            requireRelation(trip.propertyId in propertyIds, "trip ${trip.id}: missing property")
        }
        val transactionIds = transactions.map { it.transactionId }.toSet()
        val accountIds = accounts.map { it.accountId }.toSet()
        transactions.forEach { requireRelation(it.accountId in accountIds, "transaction ${it.transactionId}: missing account") }
        links.forEach { link ->
            requireRelation(link.transactionId in transactionIds && receiptById[link.receiptId]?.internalId == link.receiptInternalId,
                "bank link ${link.linkId}: missing/mismatched parent")
        }
        assignments.forEach { a ->
            requireRelation(a.transactionId in transactionIds && a.propertyId to a.unitId in unitIds, "assignment ${a.assignmentId}: missing scope")
            a.receiptId?.let { requireRelation(it in receiptById, "assignment ${a.assignmentId}: missing receipt") }
        }
        val originals = JSONArray()
        (1..60).forEach { n ->
            val doc = documents.single { it.documentId == "receipt:e10b-receipt-$n" }
            val file = File(doc.localUri.removePrefix("file://"))
            val hash = if (file.isFile) StableDocumentIdentity.sha256(file.readBytes()) else "<missing>"
            val expectedMime = mime(listOf("pdf", "jpg", "jpeg", "png", "webp")[(n - 1) % 5])
            requireRelation(file.isFile && hash == doc.sha256 && file.length() == doc.fileSizeBytes && doc.mimeType == expectedMime &&
                doc.receiptInternalId == "e10b-receipt-$n", "original $n: hash/size/MIME/receipt mismatch")
            originals.put(JSONObject().put("documentId", doc.documentId).put("receiptInternalId", doc.receiptInternalId)
                .put("sha256", hash).put("size", file.length()).put("mime", doc.mimeType).put("readable", file.isFile))
        }
        val local = label.startsWith("AFTER_LOCAL_")
        val expectedTrips = if (local) 11 else 10
        val evidence = JSONObject().put("receipts", receipts.size).put("documents", documents.size).put("properties", properties.size)
            .put("units", unitIds.size).put("trips", trips.size).put("bankTransactions", transactions.size)
            .put("bankAccounts", accounts.size).put("bankLinks", links.size).put("rentAssignments", assignments.size)
            .put("loans", db.loanDao().getAllLoans().size).put("auditRuns", db.exportAuditDao().getAllRunsFlow().first().size)
            .put("receiptIds", JSONArray(receipts.map { it.id }.sorted())).put("receiptInternalIds", JSONArray(receipts.map { it.internalId }.sorted()))
            .put("documentIds", JSONArray(documents.map { it.documentId }.sorted())).put("tripIds", JSONArray(trips.map { it.id }.sorted()))
            .put("originals", originals).put("orphans", JSONArray(problems))
        File(output, "$label.INTEGRITY.json").writeText(evidence.toString(2))
        assertTrue("$label: $problems", problems.isEmpty())
        assertEquals("$label trip count", expectedTrips, trips.size)
        assertEquals("$label receipt count", 60 + expectedTrips, receipts.size)
        assertEquals("$label document count", 60 + expectedTrips, documents.size)
        assertEquals(local, trips.any { it.id == 11L && it.expenseReceiptId == 71 })
        assertEquals(local, receiptById.containsKey(71))
        assertEquals(local, links.any { it.linkId == "e10b-local-link" })
        assertEquals(!local, links.any { it.linkId == "e10b-link" })
        val reviewed = documents.single { it.documentId == "receipt:e10b-receipt-1" }
        assertEquals(DocumentReviewStatus.GEPRUEFT.name, reviewed.reviewStatus)
        assertEquals("Bestätigte Beschreibung vor Backup", JSONObject(reviewed.extractedFieldsJson).getString("_displayDescription"))
        assertEquals(if (local) "E10B-REVIEW-LOCAL" else "E10B-REVIEW-BACKUP", JSONObject(reviewed.extractedFieldsJson).getString("rechnungNummer"))
        assertEquals(if (local) "Neuerer lokaler fachlicher Titel" else "Fachlicher Titel vor Backup", reviewed.title)
        val approved = receipts.single { it.id == 4 }
        assertTrue(com.example.util.DatevReceiptEligibility.isAccountingApproved(approved))
        assertEquals("e10b-audit-0", approved.exportlaufId)
        assertEquals("EXPORTIERT", approved.exportStatus)
    }

    private fun compare(beforeLabel: String, afterLabel: String, before: String, after: String, exactRaw: Boolean = false): Boolean {
        val name = "$beforeLabel--$afterLabel"
        val rawDiff = snapshotDiff(before, after)
        val roomRawDiff = snapshotDiff(capturedRoom.getValue(beforeLabel), capturedRoom.getValue(afterLabel))
        File(output, "$name.ROOM-RAW-DIFF.json").writeText(roomRawDiff.toString(2))
        val contractDiff = snapshotDiff(RestoreContractSnapshot.fromRaw(before, capturedRoom.getValue(beforeLabel)).canonical(),
            RestoreContractSnapshot.fromRaw(after, capturedRoom.getValue(afterLabel)).canonical())
        File(output, "$name.RAW-DIFF.json").writeText(rawDiff.toString(2))
        File(output, "$name.CONTRACT-DIFF.json").writeText(contractDiff.toString(2))
        comparisonSummary.put(name, JSONObject().put("raw", rawDiff.length()).put("roomRaw", roomRawDiff.length()).put("contract", contractDiff.length()))
        File(output, "comparison-summary.json").writeText(comparisonSummary.toString(2))
        println("PHASE10D $name RAW=${rawDiff.length()} CONTRACT=${contractDiff.length()}")
        return contractDiff.length() == 0 && (!exactRaw || (rawDiff.length() == 0 && roomRawDiff.length() == 0))
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
