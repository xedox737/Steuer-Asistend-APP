package com.example.data

import com.example.util.DatevMappingService
import com.example.util.RenovationAdvisorReport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RenovationReviewCalculatorTest {
    private val property = PropertyMetadata(propertyId = "a", name = "Haus A", notariellesKaufdatum = "2025-10-01",
        uebergangNutzenLasten = "2026-01-01", gesamtKaufpreis = 400_000.0, gebaeudewert = 320_000.0, grundUndBodenWert = 80_000.0)
    private val measure = RenovationMeasure(id = "bath", propertyId = "a", name = "Bad OG", advisorNote = "Mit Fenster DG prüfen")
    private fun receipt(id: String = "r", gross: Double = 119.0, date: String = "2026-10-10", propertyId: String = "a", description: String = "Bad") =
        Receipt(id = id.hashCode(), internalId = id, aussteller = "Testhandwerk", datum = date, uhrzeit = "", bruttobetrag = gross,
            hauptkategorie = "Renovierungs- / Reparaturkosten & Investitionen", unterkategorie = "Bad", kontoNr = "4800", beschreibung = description,
            propertyId = propertyId, positionenJson = ReceiptItemConverter.toJson(listOf(ReceiptItem(gesamtpreis = gross, steuersatz = 19.0))))
    private fun relation(id: String = "r", status: RenovationTaxStatus = RenovationTaxStatus.FUER_15_PROZENT_PRUEFUNG) =
        RenovationReceiptRelation(id, "a", measure.id, status)
    private fun calculate(receipts: List<Receipt> = listOf(receipt()), relations: List<RenovationReceiptRelation> = listOf(relation()),
        measures: List<RenovationMeasure> = listOf(measure), metadata: PropertyMetadata = property) =
        RenovationReviewCalculator.calculate(metadata, receipts, RenovationReviewSnapshot(measures, relations))

    @Test fun economicTransitionDefinesThreeYearWindow() {
        val result = calculate()
        assertEquals("2026-01-01", result.basis.monitorStartDate); assertEquals("2028-12-31", result.basis.monitorEndDate)
        assertEquals("Besitz / Nutzen / Lasten", result.startSource)
    }
    @Test fun differentObjectsHaveDifferentDatesAndBases() {
        val result = calculate(metadata = property.copy(propertyId = "b", uebergangNutzenLasten = "2024-07-15", gebaeudewert = 200_000.0, gesamtKaufpreis = 280_000.0))
        assertEquals("2024-07-15", result.basis.monitorStartDate); assertEquals("2027-07-14", result.basis.monitorEndDate)
        assertEquals(30_000.0, result.basis.limit15Percent, .01); assertTrue(result.lines.isEmpty())
    }
    @Test fun notarialFallbackIsNamedExplicitly() {
        val result = calculate(metadata = property.copy(uebergangNutzenLasten = ""))
        assertEquals("2025-10-01", result.basis.monitorStartDate); assertEquals("2028-09-30", result.basis.monitorEndDate)
        assertTrue(result.startSource.contains("Ersatz"))
    }
    @Test fun missingDatesRemainOpenAndDoNotInventPeriod() {
        val result = calculate(metadata = property.copy(uebergangNutzenLasten = "", notariellesKaufdatum = ""))
        assertEquals("", result.basis.monitorStartDate); assertEquals("", result.basis.monitorEndDate)
        assertEquals(0.0, result.consideredNet, .01); assertTrue(result.openCases.single().issue!!.contains("Prüfzeitraum fehlt"))
    }
    @Test fun acquisitionCostsUseExistingAfaSourceIncludingOnlyObjectAncillaryCosts() {
        val acquisition = receipt("acquisition", 10_000.0).copy(hauptkategorie = "Anschaffungskosten")
        val foreign = acquisition.copy(internalId = "foreign", propertyId = "b", bruttobetrag = 500_000.0)
        val result = calculate(listOf(receipt(), acquisition, foreign))
        val existing = TaxPropertyCalculator.calculate(property, listOf(receipt(), acquisition))
        assertEquals(existing.buildingAcquisitionCosts, result.basis.buildingAcquisitionCosts, .001)
        assertEquals(328_000.0, result.basis.buildingAcquisitionCosts, .01)
        assertEquals(49_200.0, result.basis.limit15Percent, .01)
    }
    @Test fun repairCategoryAloneNeverCounts() { assertEquals(0.0, calculate(relations = emptyList()).consideredNet, .01) }
    @Test fun periodLabelWorksBeforeReceiptIsAssigned() {
        val basis = calculate(relations = emptyList()).basis
        assertEquals("Innerhalb Prüfzeitraum", RenovationReviewCalculator.periodLabel(receipt(), basis))
        assertEquals("Außerhalb Prüfzeitraum", RenovationReviewCalculator.periodLabel(receipt(date = "2029-01-01"), basis))
    }
    @Test fun missingOrForeignDocumentReferenceRemainsVisibleAsReviewCase() {
        val withEvidence = measure.copy(evidence = listOf(RenovationEvidence("doc", RenovationEvidenceRole.ANGEBOT)))
        val review = RenovationReviewSnapshot(listOf(withEvidence), listOf(relation()))
        assertEquals(1, RenovationReviewCalculator.calculate(property, listOf(receipt()), review, emptyList()).errors.size)
        assertEquals(1, RenovationReviewCalculator.calculate(property, listOf(receipt()), review, listOf(ManagedDocument("doc", "b"))).errors.size)
        assertTrue(RenovationReviewCalculator.calculate(property, listOf(receipt()), review, listOf(ManagedDocument("doc", "a"))).errors.isEmpty())
    }
    @Test fun unreviewedAndAdvisorOnlyStatusesDoNotCount() {
        listOf(RenovationTaxStatus.NICHT_GEPRUEFT, RenovationTaxStatus.STEUERBERATER_PRUEFEN, RenovationTaxStatus.NICHT_BERUECKSICHTIGEN).forEach {
            assertEquals(0.0, calculate(relations = listOf(relation(status = it))).consideredNet, .01)
        }
    }
    @Test fun explicitEarmarkIncludesReliableNet() { assertEquals(100.0, calculate().consideredNet, .01) }
    @Test fun advisorConfirmationRetainsNetWithoutApprovingReceipt() {
        val r = receipt(); val result = calculate(listOf(r), listOf(relation(status = RenovationTaxStatus.STEUERBERATER_BESTAETIGT)))
        assertEquals(100.0, result.consideredNet, .01); assertEquals("OFFEN", r.freigabestatus)
        assertTrue(DatevMappingService.buildConfirmedDatevBookingRows(r, DatevProfile()).isEmpty())
    }
    @Test fun excludedMeasureSuppressesAllItsAssignments() {
        assertEquals(0.0, calculate(measures = listOf(measure.copy(taxStatus = RenovationTaxStatus.NICHT_BERUECKSICHTIGEN))).consideredNet, .01)
    }
    @Test fun creditNoteAndRefundReduceNetUsingExistingDatevSignSemantics() {
        val result = calculate(listOf(receipt(), receipt("credit", -23.8)), listOf(relation(), relation("credit")))
        assertEquals(80.0, result.consideredNet, .01)
        assertEquals(-23.8, result.lines.first { it.relation.receiptInternalId == "credit" }.grossCost, .01)
    }
    @Test fun positiveCreditNoteMarkerUsesSameSignAsDatev() {
        val result = calculate(listOf(receipt(), receipt("credit", 23.8, description = "Gutschrift Bad")), listOf(relation(), relation("credit")))
        assertEquals(80.0, result.consideredNet, .01)
    }
    @Test fun sameInternalReceiptAndRelationCountOnce() {
        val r = receipt(); assertEquals(100.0, calculate(listOf(r, r.copy(id = r.id + 1)), listOf(relation(), relation())).consideredNet, .01)
    }
    @Test fun measureChangeMovesOneRelationshipAndOneAmount() {
        val next = measure.copy(id = "window", name = "Fenster")
        val result = calculate(relations = listOf(relation().copy(renovationMeasureId = next.id)), measures = listOf(measure, next))
        assertEquals("window", result.lines.single().measure?.id); assertEquals(100.0, result.consideredNet, .01)
    }
    @Test fun generalMeasureCanContainUnitReceipt() { assertTrue(calculate(listOf(receipt().copy(unitId = "a-unit"))).lines.single().included) }
    @Test fun unitMeasureCannotIncludeOtherUnit() {
        val result = calculate(listOf(receipt().copy(unitId = "other")), measures = listOf(measure.copy(unitId = "a-unit")))
        assertFalse(result.lines.single().included); assertTrue(result.lines.single().issue!!.contains("Einheiten"))
    }
    @Test fun movedReceiptDoesNotRemainInOldObjectSum() {
        val result = calculate(listOf(receipt(propertyId = "b")))
        assertEquals(0.0, result.consideredNet, .01); assertTrue(result.lines.single().issue!!.contains("Immobilien"))
    }
    @Test fun missingReceiptAndMissingMeasureAreVisibleOpenCases() {
        assertTrue(calculate(receipts = emptyList()).openCases.single().issue!!.contains("Beleg"))
        assertTrue(calculate(measures = emptyList()).openCases.single().issue!!.contains("Maßnahme"))
    }
    @Test fun datesOutsideWindowDoNotCountButRemainVisible() {
        listOf("2025-12-31", "2029-01-01").forEach { assertEquals("Außerhalb Prüfzeitraum", calculate(listOf(receipt(date = it))).lines.single().periodLabel) }
        assertEquals(0.0, calculate(listOf(receipt(date = "2029-01-01"))).consideredNet, .01)
    }
    @Test fun bothWindowBoundaryDaysAreIncluded() {
        listOf("2026-01-01", "2028-12-31").forEach { assertEquals(100.0, calculate(listOf(receipt(date = it))).consideredNet, .01) }
    }
    @Test fun missingVatIsAnOpenCaseInsteadOfInventedNineteenPercent() {
        val result = calculate(listOf(receipt().copy(positionenJson = "")))
        assertEquals(0.0, result.consideredNet, .01); assertTrue(result.openCases.single().issue!!.contains("Nettobetrag fehlt"))
    }
    @Test fun confirmedNetCanResolveMissingVat() {
        assertEquals(95.0, calculate(listOf(receipt().copy(positionenJson = "")), listOf(relation().copy(confirmedNetAmount = 95.0))).consideredNet, .01)
    }
    @Test fun netCannotExceedGross() {
        val result = calculate(relations = listOf(relation().copy(confirmedNetAmount = 999.0)))
        assertFalse(result.lines.single().included); assertTrue(result.lines.single().issue!!.contains("Bruttobetrag"))
    }
    @Test fun warningLevelsIncludeExactBoundaryAndNeverRebook() {
        listOf(69.0 to RenovationWarning.NEUTRAL, 70.0 to RenovationWarning.HINWEIS,
            90.0 to RenovationWarning.WARNUNG, 100.0 to RenovationWarning.GRENZE_ERREICHT, 105.0 to RenovationWarning.GRENZE_ERREICHT).forEach { (percent, warning) ->
            val r = receipt(gross = 48_000.0 * percent / 100).copy(positionenJson = "")
            val result = calculate(listOf(r), listOf(relation().copy(confirmedNetAmount = r.bruttobetrag)))
            assertEquals(warning, result.warning); assertEquals(320_000.0, result.basis.buildingAcquisitionCosts, .01)
            assertEquals("OFFEN", r.freigabestatus); assertEquals("", r.allocationsJson)
        }
    }
    @Test fun reportIncludesNotesCreditsStableIdsAndOpenCasesOutsideBookingText() {
        val result = calculate(listOf(receipt(), receipt("credit", -23.8)), listOf(relation().copy(advisorMarked = true), relation("credit")))
        val report = RenovationAdvisorReport.lines(result, emptyMap(), emptyList()).joinToString("\n")
        listOf("Haus A", "15%-Grenze", "Mit Fenster DG prüfen", "Gutschriften", "80,00", "OFFENE PRÜFFÄLLE", "Steuerberater-Vormerkung", "Keine automatische Steuerentscheidung.").forEach { assertTrue(it, report.contains(it)) }
        assertEquals("Bad", result.lines.first().receipt?.beschreibung)
    }
    @Test fun crossingMeasurePeriodIsExplicitlyFlaggedInReport() {
        val report = RenovationAdvisorReport.lines(calculate(measures = listOf(measure.copy(startDate = "2028-12-01", endDate = "2029-02-01"))), emptyMap(), emptyList()).joinToString("\n")
        assertTrue(report.contains("zeitanteilige Kosten"))
    }
}
