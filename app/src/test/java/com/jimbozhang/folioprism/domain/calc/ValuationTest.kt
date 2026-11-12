package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNH
import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.GOLD
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.US_EQ
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.GoldPremium
import com.jimbozhang.folioprism.domain.model.GoldSettings
import com.jimbozhang.folioprism.domain.money.bd
import java.time.LocalDate
import org.junit.Assert.assertNull
import org.junit.Test

class ValuationTest {

    private val date = LocalDate.of(2026, 1, 1)
    private val provider = MapPriceProvider(
        prices = mapOf(
            FUND to bd("2"),
            US_EQ to bd("20"),
            GOLD to bd("500"),
        ),
        fxRates = mapOf("USD" to bd("7")),
    )

    private fun data() = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            cashInstrument(CASH_USD, "USD"),
            cashInstrument(CASH_CNH, "CNH"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
            quotedInstrument(US_EQ, CategoryIds.US_EQUITY, currency = "USD"),
            quotedInstrument(GOLD, CategoryIds.GOLD, code = "AU9999"),
        ),
    ).let { p ->
        p.copy(
            settings = p.settings.copy(
                gold = GoldSettings(premiums = listOf(GoldPremium(INST_A, bd("1.5")))),
            ),
        )
    }

    @Test
    fun cnyCash() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, CASH_CNY), bd("1000"), provider, date,
        )
        assertDecimal("1000", value)
    }

    @Test
    fun usdCashUsesFx() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, CASH_USD), bd("100"), provider, date,
        )
        assertDecimal("700", value)
    }

    @Test
    fun cnhIsTreatedAsCny() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, CASH_CNH), bd("100"), provider, date,
        )
        assertDecimal("100", value)
    }

    @Test
    fun quotedCny() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, FUND), bd("10"), provider, date,
        )
        assertDecimal("20", value)
    }

    @Test
    fun quotedUsdUsesFx() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, US_EQ), bd("10"), provider, date,
        )
        assertDecimal("1400", value)
    }

    @Test
    fun goldAddsInstitutionPremium() {
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, GOLD), bd("10"), provider, date,
        )
        assertDecimal("5015", value)
    }

    @Test
    fun missingPriceReturnsNull() {
        val emptyProvider = MapPriceProvider(fxRates = mapOf("USD" to bd("7")))
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, FUND), bd("10"), emptyProvider, date,
        )
        assertNull(value)
    }

    @Test
    fun missingFxReturnsNull() {
        val emptyFx = MapPriceProvider(prices = mapOf(US_EQ to bd("20")))
        val value = Valuation.valuePosition(
            data(), PositionKey(INST_A, US_EQ), bd("10"), emptyFx, date,
        )
        assertNull(value)
    }
}
