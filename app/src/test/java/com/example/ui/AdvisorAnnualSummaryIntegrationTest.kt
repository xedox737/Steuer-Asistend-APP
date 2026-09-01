package com.example.ui

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AccountingApprovalJson
import com.example.data.Loan
import com.example.data.PersistedAllocation
import com.example.data.PersistedBookingProposal
import com.example.data.PropertyMetadata
import com.example.data.Receipt
import com.example.util.AdvisorPackageReadinessEvaluator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdvisorAnnualSummaryIntegrationTest {
    private lateinit var application: Application
    private lateinit var context: Context

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        context = application
        approvalPrefs().edit().clear().commit()
    }

    @After
    fun tearDown() {
        approvalPrefs().edit().clear().commit()
    }

    @Test
    fun `export summary is available directly after ViewModel initialization without opening annual UI`() {
        val viewModel = ReceiptViewModel(application)

        val result = viewModel.buildAdvisorAnnualSummaryForExport(
            context = context,
            year = 2026,
            currentReceipts = emptyList(),
            currentMetadata = metadata(),
            currentLoans = listOf(loan()),
            currentUnits = emptyList()
        )

        assertEquals(2026, result.year)
        assertEquals("Testobjekt", result.propertyTitle)
        assertTrue(result.propertyOverview.isNotEmpty())
        assertTrue(result.financing.isNotEmpty())
    }

    @Test
    fun `annual UI calculation basis and export calculation return consistent values`() {
        val inputs = listOf(rentReceipt())
        val direct = buildAdvisorAnnualSummary(
            context,
            2026,
            inputs,
            metadata(),
            listOf(loan()),
            emptyList()
        )
        val viaExport = ReceiptViewModel(application).buildAdvisorAnnualSummaryForExport(
            context = context,
            year = 2026,
            currentReceipts = inputs,
            currentMetadata = metadata(),
            currentLoans = listOf(loan()),
            currentUnits = emptyList()
        )

        assertEquals(direct.totalIncome, viaExport.totalIncome, 0.001)
        assertEquals(direct.totalExpenses, viaExport.totalExpenses, 0.001)
        assertEquals(direct.dataFingerprint, viaExport.dataFingerprint)
        assertEquals(direct.openIssues, viaExport.openIssues)
    }

    @Test
    fun `changed fingerprint makes package not ready`() {
        val original = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata(),
            listOf(loan()),
            emptyList()
        )
        approve(2026, original.dataFingerprint)

        val changed = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata().copy(gesamtKaufpreis = 530_000.0),
            listOf(loan()),
            emptyList()
        )
        val readiness = AdvisorPackageReadinessEvaluator.evaluate(changed)

        assertFalse(changed.manualApprovalCurrent)
        assertEquals("NICHT BEREIT", readiness.status)
    }

    @Test
    fun `current manual approval without critical issues is advisor ready`() {
        val initial = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata(),
            listOf(loan()),
            emptyList()
        )
        approve(2026, initial.dataFingerprint)
        val approved = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata(),
            listOf(loan()),
            emptyList()
        )

        val readiness = AdvisorPackageReadinessEvaluator.evaluate(approved)

        assertTrue(readiness.blockers.joinToString(), readiness.ready)
        assertEquals("BEREIT FÜR STEUERBERATER", readiness.status)
    }

    @Test
    fun `property overview contains professional fields and no raw data class output`() {
        val result = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata(),
            emptyList(),
            emptyList()
        )
        val text = result.propertyOverview.joinToString("\n")

        assertTrue(text.contains("Objektbezeichnung: Testobjekt"))
        assertTrue(text.contains("Gesamtkaufpreis:"))
        assertTrue(text.contains("AfA-Bemessungsgrundlage"))
        assertFalse(text.contains("PropertyMetadata("))
    }

    @Test
    fun `financing contains professional fields and no raw Loan output`() {
        val result = buildAdvisorAnnualSummary(
            context,
            2026,
            emptyList(),
            metadata(),
            listOf(loan()),
            emptyList()
        )
        val text = result.financing.joinToString("\n")

        assertTrue(text.contains("Bank: Testbank"))
        assertTrue(text.contains("Darlehensbetrag:"))
        assertTrue(text.contains("Sollzins:"))
        assertTrue(text.contains("Vermietungsanteil:"))
        assertFalse(text.contains("Loan("))
    }

    @Test
    fun `missing required original keeps package blocked`() {
        val receipt = approvedReceiptWithoutOriginal()
        val initial = buildAdvisorAnnualSummary(
            context,
            2026,
            listOf(receipt),
            metadata(),
            emptyList(),
            emptyList()
        )
        approve(2026, initial.dataFingerprint)
        val approved = buildAdvisorAnnualSummary(
            context,
            2026,
            listOf(receipt),
            metadata(),
            emptyList(),
            emptyList()
        )

        val readiness = AdvisorPackageReadinessEvaluator.evaluate(approved)

        assertTrue(approved.missingRequiredOriginals.contains("BLG-receipt-3"))
        assertFalse(readiness.ready)
        assertTrue(readiness.blockers.any { it.contains("Originalunterlagen") })
    }

    private fun metadata() = PropertyMetadata(
        name = "Testobjekt",
        adresse = "Teststraße 1, 12345 Teststadt",
        wohnort = "",
        baujahr = 1954,
        wohnflaeche = 370.0,
        grundstuecksgroesse = 1_100.0,
        notariellesKaufdatum = "2025-10-01",
        uebergangNutzenLasten = "2026-01-01",
        wohneinheiten = "7 Wohneinheiten",
        gesamtKaufpreis = 520_000.0,
        gebaeudewert = 360_000.0,
        grundUndBodenWert = 160_000.0,
        kaufpreisAufteilungQuelle = "MANUELL"
    )

    private fun loan() = Loan(
        id = 1,
        bezeichnung = "Hauptdarlehen",
        bank = "Testbank",
        darlehensbetrag = 520_000.0,
        restschuld = 510_000.0,
        sollzinsProzent = 3.99,
        tilgungProzent = 1.5,
        monatlicheRate = 2_378.0,
        startDatum = "2026-01-01",
        zinsbindungBis = "2036-01-01",
        laufzeitBis = "2056-01-01",
        vermietungsanteilProzent = 100.0,
        notiz = "Testnotiz",
        aktiv = true
    )

    private fun rentReceipt() = Receipt(
        id = 10,
        aussteller = "Mieter",
        datum = "2026-02-01",
        uhrzeit = "",
        bruttobetrag = 800.0,
        hauptkategorie = "Miete, Nebenkosten & Kaution",
        unterkategorie = "Kaltmiete",
        kontoNr = "8100",
        beschreibung = "Monatsmiete",
        wohneinheit = "WE 1",
        internalId = "rent-10",
        freigabestatus = "FREIGEGEBEN"
    )

    private fun approvedReceiptWithoutOriginal(): Receipt {
        val allocation = PersistedAllocation("a", "Reparatur", 100.0, 10_000)
        val proposal = PersistedBookingProposal("a", "4801", "70000", 10_000, "")
        return Receipt(
            id = 3,
            aussteller = "Test GmbH",
            datum = "2026-08-01",
            uhrzeit = "",
            bruttobetrag = 100.0,
            hauptkategorie = "Werbungskosten",
            unterkategorie = "Reparatur",
            kontoNr = "4801",
            beschreibung = "Test",
            internalId = "receipt-3",
            allocationsJson = AccountingApprovalJson.encodeAllocations(listOf(allocation)),
            bookingProposalsJson = AccountingApprovalJson.encodeBookingProposals(listOf(proposal)),
            freigabestatus = "FREIGEGEBEN",
            exportStatus = "EXPORTBEREIT",
            pruefstatus = "GEPRUEFT"
        )
    }

    private fun approvalPrefs() =
        context.getSharedPreferences("annual_tax_approval_prefs", Context.MODE_PRIVATE)

    private fun approve(year: Int, fingerprint: String) {
        approvalPrefs().edit()
            .putString("fingerprint_$year", fingerprint)
            .putString("approved_at_$year", "2026-09-01T12:00:00+02:00")
            .commit()
    }
}
