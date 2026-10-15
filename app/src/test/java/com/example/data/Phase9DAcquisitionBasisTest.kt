package com.example.data

import com.example.ui.PropertyWizardInput
import com.example.util.DatevMappingService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Phase9DAcquisitionBasisTest {
    private val property = PropertyMetadata(propertyId = "9d-basis", name = "Basisobjekt", baujahr = 1954,
        gesamtKaufpreis = 200_000.0, gebaeudewert = 200_000.0, uebergangNutzenLasten = "2026-01-01")
    private fun receipt(id: Int, amount: Double, text: String = "Notarkosten") = Receipt(
        id, "Testnotar", "2026-01-15", "", amount, "Anschaffungskosten", "Notar", "", text,
        propertyId = property.propertyId, internalId = "9d-$id")
    private fun review(metadata: PropertyMetadata, receipts: List<Receipt>) =
        RenovationReviewCalculator.calculate(metadata, receipts, RenovationReviewSnapshot(emptyList(), emptyList()))

    @Test fun acquisitionCreditReducesAfaAndFifteenPercentFromExactlyTheSameBasis() {
        val receipts = listOf(receipt(1, 10_000.0), receipt(2, -1_000.0, "Korrekturgutschrift"))
        val original = receipts.toList()
        val tax = TaxPropertyCalculator.calculate(property, receipts)
        val renovation = review(property, receipts)
        assertEquals(209_000.0, tax.buildingAcquisitionCosts, .001)
        assertEquals(4_180.0, tax.annualAfa, .001)
        assertEquals(31_350.0, tax.limit15Percent, .001)
        assertEquals(tax, renovation.basis)
        assertEquals(-1_000.0, tax.acquisitionCostDetails[1].grossAmount, .001)
        assertNull(tax.acquisitionBasisError)
        assertEquals(original, receipts) // no receipt, identity or approval mutation
        assertEquals("OFFEN", receipts[1].freigabestatus)
    }

    @Test fun negativeReceiptAndPositiveCreditMarkersUseTheAuthoritativeExistingConvention() {
        listOf(receipt(2, -1_000.0, "Korrektur"), receipt(2, 1_000.0, "Gutschrift"),
            receipt(2, 1_000.0, "Storno Notarkosten")).forEach { credit ->
            assertTrue(DatevMappingService.isCreditNote(credit))
            val result = TaxPropertyCalculator.calculate(property, listOf(receipt(1, 10_000.0), credit))
            assertEquals(209_000.0, result.buildingAcquisitionCosts, .001)
            assertEquals(-1_000.0, result.acquisitionCostDetails[1].grossAmount, .001)
        }
    }

    @Test fun multipleCreditsKeepAllocationAfterTheSignedAcquisitionAmounts() {
        val metadata = property.copy(gesamtKaufpreis = 250_000.0, grundUndBodenWert = 50_000.0)
        val receipts = listOf(receipt(1, 10_000.0), receipt(2, -1_000.0), receipt(3, -500.0))
        val tax = TaxPropertyCalculator.calculate(metadata, receipts)
        assertEquals(8_500.0, tax.acquisitionAncillaryGross, .001)
        assertEquals(6_800.0, tax.buildingAncillaryShare, .001)
        assertEquals(206_800.0, tax.buildingAcquisitionCosts, .001)
        assertEquals(4_136.0, tax.annualAfa, .001)
        assertEquals(31_020.0, tax.limit15Percent, .001)
        assertEquals(tax, review(metadata, receipts).basis)
    }

    @Test fun ordinaryPositiveCostsAndExistingReceiptScopeRemainUnchanged() {
        val good = receipt(1, 10_000.0)
        val ignored = listOf(receipt(2, -500.0).copy(hauptkategorie = "Unklassifiziert"),
            receipt(3, -500.0).copy(deletionStatus = "DELETED"))
        val tax = TaxPropertyCalculator.calculate(property, listOf(good) + ignored)
        assertEquals(210_000.0, tax.buildingAcquisitionCosts, .001)
        assertEquals(4_200.0, tax.annualAfa, .001)
        assertEquals(31_500.0, tax.limit15Percent, .001)
        assertEquals(1, tax.acquisitionCostDetails.size)
        val foreign = good.copy(id = 4, internalId = "foreign", propertyId = "other", bruttobetrag = 900_000.0)
        assertEquals(tax, review(property, listOf(good, foreign) + ignored).basis)
    }

    @Test fun creditsCannotCreateANegativeDepreciationBasisAndTheirEvidenceIsRetained() {
        val credit = receipt(2, -300_000.0, "Gutschrift")
        val tax = TaxPropertyCalculator.calculate(property, listOf(credit))
        assertNotNull(tax.acquisitionBasisError)
        assertTrue(tax.allocationNeedsReview)
        assertEquals(0.0, tax.buildingAcquisitionCosts, 0.0)
        assertEquals(0.0, tax.annualAfa, 0.0)
        assertEquals(0.0, tax.limit15Percent, 0.0)
        assertEquals(-300_000.0, tax.acquisitionCostDetails.single().grossAmount, 0.0)
        assertTrue(review(property, listOf(credit)).errors.any { it == tax.acquisitionBasisError })
        assertEquals(-300_000.0, credit.bruttobetrag, 0.0)
    }

    @Test fun malformedBaseValuesAndNonfiniteAcquisitionAmountsAreReviewCasesNotCredits() {
        listOf(property.copy(gebaeudewert = -200_000.0), property.copy(gesamtKaufpreis = -1.0),
            property.copy(grundUndBodenWert = -1.0)).forEach {
            val result = TaxPropertyCalculator.calculate(it, emptyList())
            assertNotNull(result.acquisitionBasisError)
            assertEquals(0.0, result.annualAfa, 0.0)
        }
        val result = TaxPropertyCalculator.calculate(property, listOf(receipt(1, Double.POSITIVE_INFINITY)))
        assertNotNull(result.acquisitionBasisError)
        assertEquals(0.0, result.buildingAcquisitionCosts, 0.0)
        val invalidInput = PropertyWizardInput.validate("-100", "72,5", "100", "2026-01-01", "1954", "", "", "")
        assertNull(invalidInput.values)
        assertTrue(invalidInput.errors.containsKey(PropertyWizardInput.PURCHASE_PRICE))
    }
}
