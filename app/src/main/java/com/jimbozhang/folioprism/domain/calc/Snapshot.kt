package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.instrument
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val ONE_HUNDRED = BigDecimal(100)
private const val RATIO_SCALE = 10
private const val AMOUNT_SCALE = 10

data class CategorySnapshot(
    val category: Category,
    val valueCny: BigDecimal,
    /** Share as a fraction, e.g. 0.32. */
    val share: BigDecimal,
    /** Target share as a fraction, e.g. 0.32. */
    val targetShare: BigDecimal,
    val targetValueCny: BigDecimal?,
    val minValueCny: BigDecimal?,
    val maxValueCny: BigDecimal?,
    val inRange: Boolean,
    val gapToTargetCny: BigDecimal?,
    val gapToMinCny: BigDecimal?,
    val gapToMaxCny: BigDecimal?,
    val incomplete: Boolean,
    val goldMinPriceCnyPerGram: BigDecimal? = null,
    val goldMaxPriceCnyPerGram: BigDecimal? = null,
    /** Shares using totalValue as the common visual denominator. */
    val visualShare: BigDecimal = BigDecimal.ZERO,
    val visualTarget: BigDecimal = BigDecimal.ZERO,
    val visualMin: BigDecimal = BigDecimal.ZERO,
    val visualMax: BigDecimal = BigDecimal.ZERO,
)

data class PortfolioSnapshot(
    val totalValueCny: BigDecimal,
    val dividendValueCny: BigDecimal,
    val dividendShare: BigDecimal,
    val portfolioValueCny: BigDecimal,
    val portfolioShare: BigDecimal,
    val categories: List<CategorySnapshot>,
    val brokerValues: Map<String, BigDecimal>,
    val currencyValues: Map<String, BigDecimal>,
    val positionValues: Map<PositionKey, BigDecimal?>,
    val hasMissingPrices: Boolean,
)

object SnapshotCalculator {

    fun build(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
        provider: PriceProvider,
        date: LocalDate,
    ): PortfolioSnapshot {
        val positionValues = linkedMapOf<PositionKey, BigDecimal?>()
        val categoryValues = linkedMapOf<String, BigDecimal>()
        val incompleteCategories = mutableSetOf<String>()
        val brokerValues = linkedMapOf<String, BigDecimal>()
        val currencyValues = linkedMapOf<String, BigDecimal>()
        var total = BigDecimal.ZERO
        var hasMissingPrices = false

        positions.forEach { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId)
            val value = Valuation.valuePosition(data, key, quantity, provider, date)
            positionValues[key] = value

            if (value == null) {
                if (quantity.signum() != 0) {
                    hasMissingPrices = true
                    instrument?.let { incompleteCategories.add(it.categoryId) }
                }
                return@forEach
            }

            total = total.add(value)
            brokerValues[key.institutionId] =
                (brokerValues[key.institutionId] ?: BigDecimal.ZERO).add(value)
            instrument?.let { ins ->
                categoryValues[ins.categoryId] =
                    (categoryValues[ins.categoryId] ?: BigDecimal.ZERO).add(value)
                val exposure = exposureOf(ins)
                currencyValues[exposure] =
                    (currencyValues[exposure] ?: BigDecimal.ZERO).add(value)
            }
        }

        val dividendCategoryIds = data.settings.categories
            .filter { it.specialRule == "dividend" }
            .map { it.id }
            .toSet()

        var dividendValue = BigDecimal.ZERO
        dividendCategoryIds.forEach { categoryId ->
            dividendValue = dividendValue.add(categoryValues[categoryId] ?: BigDecimal.ZERO)
        }
        val portfolioValue = total.subtract(dividendValue)

        val categories = data.settings.categories
            .sortedBy { it.order }
            .map { category ->
                buildCategorySnapshot(
                    data = data,
                    category = category,
                    positions = positions,
                    valueCny = categoryValues[category.id] ?: BigDecimal.ZERO,
                    totalValue = total,
                    portfolioValue = portfolioValue,
                    incomplete = incompleteCategories.contains(category.id),
                )
            }

        return PortfolioSnapshot(
            totalValueCny = total,
            dividendValueCny = dividendValue,
            dividendShare = ratio(dividendValue, total),
            portfolioValueCny = portfolioValue,
            portfolioShare = ratio(portfolioValue, total),
            categories = categories,
            brokerValues = brokerValues,
            currencyValues = currencyValues,
            positionValues = positionValues,
            hasMissingPrices = hasMissingPrices,
        )
    }

    private fun buildCategorySnapshot(
        data: PortfolioData,
        category: Category,
        positions: Map<PositionKey, BigDecimal>,
        valueCny: BigDecimal,
        totalValue: BigDecimal,
        portfolioValue: BigDecimal,
        incomplete: Boolean,
    ): CategorySnapshot {
        val isDividend = category.specialRule == "dividend"
        val base = if (isDividend) totalValue else portfolioValue
        val share = ratio(valueCny, base)
        val targetShare = fraction(category.targetPct)
        val minShare = fraction(category.minPct)
        val maxShare = fraction(category.maxPct)

        val targetValue = dynAmount(targetShare, base, valueCny)
        val minValue = dynAmount(minShare, base, valueCny)
        val maxValue = dynAmount(maxShare, base, valueCny)

        val goldPriceThresholds =
            if (category.id == CategoryIds.GOLD) {
                computeGoldThresholds(
                    data = data,
                    positions = positions,
                    portfolioValue = portfolioValue,
                    goldCategoryValue = valueCny,
                    minShare = minShare,
                    maxShare = maxShare,
                )
            } else {
                null
            }

        return CategorySnapshot(
            category = category,
            valueCny = valueCny,
            share = share,
            targetShare = targetShare,
            targetValueCny = targetValue,
            minValueCny = minValue,
            maxValueCny = maxValue,
            inRange = share >= minShare && share <= maxShare,
            gapToTargetCny = targetValue?.let { valueCny.subtract(it) },
            gapToMinCny = minValue?.let { valueCny.subtract(it) },
            gapToMaxCny = maxValue?.let { valueCny.subtract(it) },
            incomplete = incomplete,
            goldMinPriceCnyPerGram = goldPriceThresholds?.first,
            goldMaxPriceCnyPerGram = goldPriceThresholds?.second,
            visualShare = ratio(valueCny, totalValue),
            visualTarget = ratio(targetShare.multiply(base), totalValue),
            visualMin = ratio(minShare.multiply(base), totalValue),
            visualMax = ratio(maxShare.multiply(base), totalValue),
        )
    }

    /**
     * Returns the base gold price (CNY/g) at which the category reaches the min/max share.
     */
    private fun computeGoldThresholds(
        data: PortfolioData,
        positions: Map<PositionKey, BigDecimal>,
        portfolioValue: BigDecimal,
        goldCategoryValue: BigDecimal,
        minShare: BigDecimal,
        maxShare: BigDecimal,
    ): Pair<BigDecimal?, BigDecimal?> {
        var grams = BigDecimal.ZERO
        positions.forEach { (key, quantity) ->
            val instrument = data.instrument(key.instrumentId)
            if (instrument != null &&
                instrument.categoryId == CategoryIds.GOLD &&
                instrument.kind == InstrumentKind.QUOTED &&
                Valuation.isGold(instrument)
            ) {
                grams = grams.add(quantity)
            }
        }
        val otherValue = portfolioValue.subtract(goldCategoryValue)
        if (grams.signum() <= 0 || otherValue.signum() <= 0) {
            return null to null
        }
        return threshold(minShare, otherValue, grams) to threshold(maxShare, otherValue, grams)
    }

    private fun threshold(
        share: BigDecimal,
        otherValue: BigDecimal,
        grams: BigDecimal,
    ): BigDecimal? {
        val oneMinusShare = BigDecimal.ONE.subtract(share)
        if (oneMinusShare.signum() <= 0) return null
        val denominator = oneMinusShare.multiply(grams)
        if (denominator.signum() == 0) return null
        return share.multiply(otherValue)
            .divide(denominator, AMOUNT_SCALE, RoundingMode.HALF_UP)
    }

    /**
     * dynAmount(p, base, value) = p * (base - value) / (1 - p)
     * p is a fraction. Returns null when 1 - p <= 0.
     */
    fun dynAmount(
        p: BigDecimal,
        base: BigDecimal,
        value: BigDecimal,
    ): BigDecimal? {
        val oneMinusP = BigDecimal.ONE.subtract(p)
        if (oneMinusP.signum() <= 0) return null
        return p.multiply(base.subtract(value))
            .divide(oneMinusP, AMOUNT_SCALE, RoundingMode.HALF_UP)
    }

    fun fraction(percent: BigDecimal): BigDecimal =
        percent.divide(ONE_HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP)

    fun ratio(numerator: BigDecimal, denominator: BigDecimal): BigDecimal =
        if (denominator.signum() == 0) BigDecimal.ZERO
        else numerator.divide(denominator, RATIO_SCALE, RoundingMode.HALF_UP)

    fun exposureOf(instrument: Instrument): String {
        val override = instrument.currencyExposure.uppercase()
        if (override != "AUTO") return override
        return when (instrument.currency.uppercase()) {
            "CNH" -> "CNY"
            else -> instrument.currency.uppercase()
        }
    }
}
