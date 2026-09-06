package com.example.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountingApprovalJson
import com.example.data.DatevProfile
import com.example.data.Loan
import com.example.data.PersistedAllocation
import com.example.data.PersistedBookingProposal
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.data.SyntheticDocumentFixtureFactory
import com.example.util.AdvisorPackageBuilder
import com.example.util.AdvisorPackageReadinessEvaluator
import com.example.util.AdvisorPackageStructure
import com.example.util.AdvisorPackageStructureValidator
import com.example.util.BookingValidationService
import com.example.util.DatevConfig
import com.example.util.DatevExporter
import com.example.util.DatevFormatValidator
import com.example.util.DatevMappingService
import com.example.util.DatevOriginalAttachmentPolicy
import com.example.util.DatevReceiptEligibility
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyntheticTaxExportE2eTest {
    private lateinit var application: Application
    private lateinit var context: Context
    private lateinit var data: SyntheticTaxData

    @Before fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        approvalPrefs().edit().clear().commit()
        context.getSharedPreferences("loan_interest_assignments", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("rent_plan_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        data = createTaxData()
        context.getSharedPreferences("loan_interest_assignments", Context.MODE_PRIVATE).edit()
            .putInt("receipt_${data.interestReceipt.id}", data.loan.id).commit()
        TenantHistoryStore.save(
            context,
            "WE_01",
            listOf(TenantPeriod(1, "WE_01", "Testmieter", "2026-01-01", "2026-01-31", 850.0, 150.0, 0.0))
        )
    }

    @After fun tearDown() {
        approvalPrefs().edit().clear().commit()
        context.getSharedPreferences("loan_interest_assignments", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("tenant_history_prefs", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun `same synthetic receipts pass real DATEV and Advisor package end to end`() {
        val profile = DatevProfile.createDefaultSkr03().copy(profileName = "Synthetic Property 2026")
        val config = DatevConfig(wirtschaftsjahr = 2026, propertyName = profile.profileName)

        val datevCsv = DatevExporter.generateBuchungsstapelCsv(data.approvedReceipts, config)
        assertTrue(DatevFormatValidator.validate(datevCsv).isValid)
        assertTrue(datevCsv.contains("1249,90"))
        assertTrue(datevCsv.contains("84,50"))

        val records = data.approvedReceipts.flatMap { DatevMappingService.buildConfirmedDatevBookingRows(it, profile) }
        val validation = BookingValidationService.validateRecords(records, profile)
        assertTrue(validation.errors.joinToString { it.message }, validation.isValidForExport)
        assertEquals(data.approvedReceipts.size, records.size)
        assertEquals(data.approvedReceipts.map { it.id }.toSet(), records.map { it.receiptId }.toSet())
        records.forEach { assertTrue(datevCsv.contains(it.belegfeld1)) }
        assertEquals(data.approvedReceipts.sumOf { it.bruttobetrag }, records.sumOf { it.bruttobetrag }, 0.001)

        val initialSummary = ReceiptViewModel(application).buildAdvisorAnnualSummaryForExport(
            context, 2026, data.approvedReceipts, data.metadata, listOf(data.loan), listOf(data.unit)
        )
        approvalPrefs().edit()
            .putString("fingerprint_2026", initialSummary.dataFingerprint)
            .putString("approved_at_2026", "2026-09-06T12:00:00Z")
            .commit()
        val summary = ReceiptViewModel(application).buildAdvisorAnnualSummaryForExport(
            context, 2026, data.approvedReceipts, data.metadata, listOf(data.loan), listOf(data.unit)
        )

        assertTrue(summary.manualApprovalCurrent)
        assertEquals(initialSummary.dataFingerprint, summary.approvedFingerprint)
        assertEquals(1_000.0, summary.totalIncome, 0.001)
        assertTrue(summary.totalExpenses > 12_345.67)
        assertEquals(12_345.67, summary.expenseValues.single { it.label == "Schuldzinsen" }.amount, 0.001)
        assertTrue(summary.expenseValues.single { it.label == "AfA Gebäude" }.amount > 0.0)
        assertTrue(summary.rentOverview.any { it.contains("WE_01") })
        assertTrue(summary.missingRequiredOriginals.isEmpty())
        assertTrue(AdvisorPackageReadinessEvaluator.evaluate(summary).ready)

        val result = AdvisorPackageBuilder.buildPackage(
            context = context,
            records = records,
            includedReceipts = data.approvedReceipts,
            excludedReceipts = data.excludedReceipts,
            includeOriginals = true,
            profile = profile,
            validationReport = validation,
            periodSummary = "2026",
            annualSummary = summary
        )
        assertTrue(result.packageStructureVerified)
        assertEquals("BEREIT FÜR STEUERBERATER", result.advisorStatus)

        ZipFile(result.zipFile).use { zip ->
            val entries = zip.entries().asSequence().toList()
            val names = entries.map { it.name }
            AdvisorPackageStructure.requiredFolders.forEach { folder ->
                assertTrue("Pflichtordner fehlt: $folder", names.any { it == "$folder/" || it.startsWith("$folder/") })
            }
            val startPdf = zip.getInputStream(zip.getEntry("00_Start/01_Jahresuebersicht.pdf")).readBytes()
            assertArrayEquals("%PDF".toByteArray(), startPdf.copyOfRange(0, 4))
            assertTrue(names.contains("03_Anlage_V/01_Anlage_V_Vorschau.csv"))
            assertTrue(names.contains("03_Anlage_V/02_Werteherkunft_und_Pruefstatus.csv"))
            val datevEntry = names.single { it.startsWith("01_DATEV/EXTF_Buchungsstapel_") }
            val packagedCsv = zip.getInputStream(zip.getEntry(datevEntry)).readBytes()
                .toString(Charsets.UTF_8).removePrefix("\uFEFF")
            assertTrue(DatevFormatValidator.validate(packagedCsv).isValid)
            val originals = entries.filter { !it.isDirectory && it.name.startsWith("02_Originalbelege/") }
            assertEquals(data.approvedReceipts.size, originals.size)
            val originalHashes = originals.map { entry -> sha256(zip.getInputStream(entry).readBytes()) }
            assertEquals(originalHashes.size, originalHashes.distinct().size)
            assertTrue(AdvisorPackageStructureValidator.validateEntryNames(names, originalHashes).valid)
            val excludedText = zip.getInputStream(zip.getEntry("08_Pruefprotokoll/Nicht_exportierte_Belege.csv"))
                .bufferedReader().readText()
            assertTrue(excludedText.contains(data.unreviewed.aussteller))
            assertFalse(packagedCsv.contains(data.unreviewed.aussteller))
        }
    }

    @Test fun `DATEV production gates reject unreviewed exported incomplete and duplicate identities`() {
        assertTrue(DatevReceiptEligibility.issues(data.unreviewed).any { it.code == "NOT_APPROVED" })
        assertTrue(DatevReceiptEligibility.issues(data.alreadyExported).any { it.code == "ALREADY_EXPORTED" })
        assertTrue(DatevReceiptEligibility.issues(data.incomplete).any { it.code == "INVALID_ALLOCATION" || it.code == "MISSING_BOOKING_PROPOSAL" })

        val duplicatePair = listOf(data.approvedReceipts.first(), data.duplicateIdentity)
        assertEquals(setOf(data.approvedReceipts.first().internalId), DatevReceiptEligibility.duplicateInternalIds(duplicatePair))
        assertThrows(IllegalArgumentException::class.java) {
            DatevExporter.generateBuchungsstapelCsv(duplicatePair, DatevConfig(wirtschaftsjahr = 2026))
        }

        val uniqueOriginals = DatevOriginalAttachmentPolicy.unique(duplicatePair)
        assertEquals(1, uniqueOriginals.size)
        assertEquals(File(data.approvedReceipts.first().imageUrl).canonicalPath, uniqueOriginals.single().file.canonicalPath)
        assertArrayEquals(File(data.approvedReceipts.first().imageUrl).readBytes(), uniqueOriginals.single().file.readBytes())
    }

    @Test fun `Advisor readiness blocks stale changed critical and missing original states`() {
        val viewModel = ReceiptViewModel(application)
        val initial = viewModel.buildAdvisorAnnualSummaryForExport(
            context, 2026, data.approvedReceipts, data.metadata, listOf(data.loan), listOf(data.unit)
        )
        approvalPrefs().edit().putString("fingerprint_2026", initial.dataFingerprint).putString("approved_at_2026", "now").commit()
        val approved = viewModel.buildAdvisorAnnualSummaryForExport(
            context, 2026, data.approvedReceipts, data.metadata, listOf(data.loan), listOf(data.unit)
        )
        assertTrue(AdvisorPackageReadinessEvaluator.evaluate(approved).ready)

        val profile = DatevProfile.createDefaultSkr03().copy(profileName = "Synthetic Property 2026")
        val records = data.approvedReceipts.flatMap { DatevMappingService.buildConfirmedDatevBookingRows(it, profile) }
        val repeatedRecords = records + records.first().copy(bookingId = records.first().bookingId + "_SECOND_ALLOCATION")
        val repeatedValidation = BookingValidationService.validateRecords(repeatedRecords, profile)
        val repeatedResult = AdvisorPackageBuilder.buildPackage(
            context, repeatedRecords, data.approvedReceipts, data.excludedReceipts, true,
            profile, repeatedValidation, "2026", approved
        )
        ZipFile(repeatedResult.zipFile).use { zip ->
            val originalCount = zip.entries().asSequence()
                .count { !it.isDirectory && it.name.startsWith("02_Originalbelege/") }
            assertEquals(data.approvedReceipts.size, originalCount)
        }

        val changedReceipts = data.approvedReceipts.mapIndexed { index, receipt ->
            if (index == 0) approvedReceipt(receipt.copy(bruttobetrag = receipt.bruttobetrag + 10.0), receipt.kontoNr)
            else receipt
        }
        val changed = viewModel.buildAdvisorAnnualSummaryForExport(
            context, 2026, changedReceipts, data.metadata, listOf(data.loan), listOf(data.unit)
        )
        assertNotEquals(approved.dataFingerprint, changed.dataFingerprint)
        assertFalse(changed.manualApprovalCurrent)
        assertFalse(AdvisorPackageReadinessEvaluator.evaluate(changed).ready)
        assertFalse(AdvisorPackageReadinessEvaluator.evaluate(approved.copy(criticalAnnualIssues = 1)).ready)
        assertFalse(AdvisorPackageReadinessEvaluator.evaluate(approved.copy(criticalClosingChecks = 1)).ready)
        val missingOriginal = approved.copy(missingRequiredOriginals = listOf("BLG-MISSING"))
        assertFalse(AdvisorPackageReadinessEvaluator.evaluate(missingOriginal).ready)
        val missingResult = AdvisorPackageBuilder.buildPackage(
            context, records, data.approvedReceipts, data.excludedReceipts, true,
            profile, BookingValidationService.validateRecords(records, profile), "2026", missingOriginal
        )
        assertEquals("NICHT BEREIT", missingResult.advisorStatus)
    }

    private fun createTaxData(): SyntheticTaxData {
        val root = Files.createTempDirectory("synthetic-tax-e2e-").toFile()
        val files = SyntheticDocumentFixtureFactory.materialize(root)
        fun receipt(id: Int, fixture: Int, vendor: String, amount: Double, category: String, subcategory: String, account: String, description: String, unit: String = "") =
            approvedReceipt(
                Receipt(
                    id = id,
                    aussteller = vendor,
                    datum = if (id == 1) "2026-02-14" else "2026-01-${id.toString().padStart(2, '0')}",
                    uhrzeit = "",
                    bruttobetrag = amount,
                    hauptkategorie = category,
                    unterkategorie = subcategory,
                    kontoNr = account,
                    beschreibung = description,
                    imageUrl = files.getValue(fixture).absolutePath,
                    wohneinheit = unit,
                    internalId = "tax-e2e-$id",
                    displayId = "BLG-2026-${id.toString().padStart(5, '0')}",
                    driveFolderId = "legacy-category-folder-$id",
                    storedFilename = files.getValue(fixture).name,
                    syncStatus = "SYNCED"
                ),
                account
            )

        val approved = listOf(
            receipt(1, 1, "TestHandwerk GmbH", 1_249.90, "Renovierungs- / Reparaturkosten & Investitionen", "Reparatur", "4801", "Reparatur Heizungsventil", "WE_01"),
            receipt(2, 2, "TestBaumarkt GmbH", 84.50, "Instandhaltung & Reparaturen", "Baumaterial", "4801", "Material Reparatur", "WE_01"),
            receipt(3, 3, "TestVersicherung AG", 417.00, "Gebäudeversicherung & Haftpflicht", "Gebäudeversicherung", "4360", "Jahresbeitrag 2026"),
            receipt(4, 4, "Gemeinde Musterstadt", 360.00, "Grundsteuer & öffentliche Abgaben", "Grundsteuer", "4360", "Grundsteuer 2026"),
            receipt(5, 7, "TestVerwaltung GmbH", 125.00, "Verwaltungskosten & Hausverwaltung", "Verwaltung", "4950", "Objektverwaltung"),
            receipt(6, 13, "Testmieter", 1_000.00, "Miete, Nebenkosten & Kaution", "Kaltmiete und Nebenkosten", "8100", "Miete Januar", "WE_01"),
            receipt(7, 11, "Testbank AG", 12_345.67, "Finanzierung, Kredite & Versicherungen", "Kreditzinsen", "2110", "Schuldzinsen 2026")
        )
        val unreviewed = approved.first().copy(
            id = 101, internalId = "tax-e2e-unreviewed", displayId = "BLG-2026-UNREVIEWED",
            aussteller = "Ungepruefter KI Test",
            freigabestatus = "OFFEN", pruefstatus = "UNGEPRUEFT", exportStatus = "KI_VORSCHLAG"
        )
        val alreadyExported = approved[1].copy(id = 102, internalId = "tax-e2e-exported", exportStatus = "EXPORTIERT")
        val incomplete = approved[2].copy(
            id = 103, internalId = "tax-e2e-incomplete", allocationsJson = "", bookingProposalsJson = ""
        )
        return SyntheticTaxData(
            approvedReceipts = approved,
            unreviewed = unreviewed,
            alreadyExported = alreadyExported,
            incomplete = incomplete,
            duplicateIdentity = approved.first().copy(id = 104),
            metadata = PropertyMetadata(
                propertyId = "property-tax-e2e",
                name = "Synthetisches Testobjekt",
                adresse = "Musterstrasse 12, 12345 Musterstadt",
                baujahr = 1998,
                wohnflaeche = 120.0,
                grundstuecksgroesse = 500.0,
                notariellesKaufdatum = "2025-10-01",
                uebergangNutzenLasten = "2026-01-01",
                wohneinheiten = "WE_01",
                gesamtKaufpreis = 520_000.0,
                gebaeudewert = 390_000.0,
                grundUndBodenWert = 130_000.0,
                kaufpreisAufteilungQuelle = "SYNTHETISCHER_TEST"
            ),
            loan = Loan(
                id = 1,
                bezeichnung = "Testdarlehen",
                bank = "Testbank AG",
                darlehensbetrag = 450_000.0,
                restschuld = 440_000.0,
                sollzinsProzent = 3.99,
                tilgungProzent = 1.5,
                monatlicheRate = 2_100.0,
                startDatum = "2025-10-01",
                zinsbindungBis = "2035-10-01",
                laufzeitBis = "2055-10-01"
            ),
            unit = WohneinheitStatus("WE_01", "Wohnung 1", "Vermietet", "Testmieter", 850.0, 80.0, "2026-01-01", "unit-test-01"),
            interestReceipt = approved.single { it.id == 7 }
        )
    }

    private fun approvedReceipt(receipt: Receipt, account: String): Receipt {
        val amountCent = Math.round(receipt.bruttobetrag * 100.0)
        val allocationId = "allocation-${receipt.internalId}"
        return receipt.copy(
            allocationsJson = AccountingApprovalJson.encodeAllocations(
                listOf(PersistedAllocation(allocationId, receipt.beschreibung, 100.0, amountCent))
            ),
            bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(
                listOf(PersistedBookingProposal(allocationId, account, "70000", amountCent, ""))
            ),
            freigabestatus = "FREIGEGEBEN",
            pruefstatus = "GEPRUEFT",
            exportStatus = "EXPORTBEREIT"
        )
    }

    private fun approvalPrefs() = context.getSharedPreferences("annual_tax_approval_prefs", Context.MODE_PRIVATE)
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private data class SyntheticTaxData(
    val approvedReceipts: List<Receipt>,
    val unreviewed: Receipt,
    val alreadyExported: Receipt,
    val incomplete: Receipt,
    val duplicateIdentity: Receipt,
    val metadata: PropertyMetadata,
    val loan: Loan,
    val unit: WohneinheitStatus,
    val interestReceipt: Receipt
) {
    val excludedReceipts: List<Receipt> get() = listOf(unreviewed, alreadyExported, incomplete, duplicateIdentity)
}
