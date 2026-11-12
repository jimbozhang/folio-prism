package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GOLD
import com.jimbozhang.folioprism.domain.GROWTH
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.money.bd
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotTest {

    private val date = LocalDate.of(2026, 1, 1)

    private val prices = mapOf(
        FUND to bd("32"),
        GROWTH to bd("24"),
        US_EQ to bd("20"),
        GOLD to bd("1000"),
    )
    private val provider = MapPriceProvider(
        prices = prices,
        fxRates = mapOf("USD" to bd("7")),
    )

    private fun data() = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.DIVIDEND),
            quotedInstrument(GROWTH, CategoryIds.GROWTH),
            quotedInstrument(US_EQ, CategoryIds.US_EQUITY, currency = "USD"),
            quotedInstrument(GOLD, CategoryIds.GOLD, code = "AU9999"),
        ),
    )

    private fun positions() = mapOf(
        PositionKey(INST_A, CASH_CNY) to bd("1000"),
        PositionKey(INST_A, FUND) to bd("10"),
        PositionKey(INST_A, GROWTH) to bd("10"),
        PositionKey(INST_A, US_EQ) to bd("10"),
        PositionKey(INST_A, GOLD) to bd("1"),
    )

    @Test
    fun totalsUseDualDenominator() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)

        assertDecimal("3960", snapshot.totalValueCny)
        assertDecimal("320", snapshot.dividendValueCny)
        assertDecimal("3640", snapshot.portfolioValueCny)
        assertDecimal("0.0808080808", snapshot.dividendShare)
    }

    @Test
    fun dividendCategoryUsesTotalValueAsBase() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        val dividend = snapshot.categories.first { it.category.id == CategoryIds.DIVIDEND }

        // Target amount at which dividend / total = 32%, with all other assets fixed.
        assertDecimal("1712.9411764706", dividend.targetValueCny)
        assertDecimal("0.32", dividend.targetShare)
    }

    @Test
    fun nonDividendCategoryUsesPortfolioValueAsBase() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        val growth = snapshot.categories.first { it.category.id == CategoryIds.GROWTH }

        assertDecimal("240", growth.valueCny)
        assertDecimal("0.0659340659", growth.share)
        assertDecimal("1073.6842105263", growth.targetValueCny)
    }

    @Test
    fun categorySharesUseApplicableBaseButVisualsUseTotal() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        val growth = snapshot.categories.first { it.category.id == CategoryIds.GROWTH }

        assertDecimal("0.0606060606", growth.visualShare)
        assertDecimal("0.2206060606", growth.visualTarget)
    }

    @Test
    fun inRangeUsesCategoryShare() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        val gold = snapshot.categories.first { it.category.id == CategoryIds.GOLD }

        assertDecimal("0.2747252747", gold.share)
        assertTrue(gold.inRange)
        assertFalse(snapshot.categories.first { it.category.id == CategoryIds.US_BOND }.inRange)
    }

    @Test
    fun goldThresholdsAreComputed() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        val gold = snapshot.categories.first { it.category.id == CategoryIds.GOLD }

        // p * other / ((1 - p) * grams), other = 3640 - 1000 = 2640, grams = 1
        assertDecimal("976.4383561644", gold.goldMinPriceCnyPerGram)
        assertDecimal("1300.2985074627", gold.goldMaxPriceCnyPerGram)
    }

    @Test
    fun currencyExposureUsesInstrumentCurrency() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)

        assertDecimal("1400", snapshot.currencyValues["USD"])
        assertDecimal("2560", snapshot.currencyValues["CNY"])
    }

    @Test
    fun brokerValuesAggregateByInstitution() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)

        assertDecimal("3960", snapshot.brokerValues[INST_A])
    }

    @Test
    fun missingPriceMarksCategoryIncomplete() {
        val providerWithoutGrowth = MapPriceProvider(
            prices = prices - GROWTH,
            fxRates = mapOf("USD" to bd("7")),
        )
        val snapshot = SnapshotCalculator.build(data(), positions(), providerWithoutGrowth, date)
        val growth = snapshot.categories.first { it.category.id == CategoryIds.GROWTH }

        assertTrue(snapshot.hasMissingPrices)
        assertTrue(growth.incomplete)
        assertDecimal("3720", snapshot.totalValueCny)
    }

    @Test
    fun dynAmountMatchesInvestFolioFormula() {
        assertDecimal("80", SnapshotCalculator.dynAmount(bd("0.5"), bd("100"), bd("20")))
        assertNull(SnapshotCalculator.dynAmount(bd("1"), bd("100"), bd("20")))
    }

    @Test
    fun categoryListFollowsConfiguredOrder() {
        val snapshot = SnapshotCalculator.build(data(), positions(), provider, date)
        assertEquals(CategoryIds.DIVIDEND, snapshot.categories.first().category.id)
        assertEquals(CategoryIds.CASH, snapshot.categories.last().category.id)
    }
}
