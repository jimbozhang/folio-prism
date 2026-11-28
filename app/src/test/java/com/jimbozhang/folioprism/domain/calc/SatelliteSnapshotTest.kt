package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import java.time.LocalDate
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelliteSnapshotTest {

    private val date = LocalDate.of(2026, 1, 1)
    private val hkStock = "ins_hk"
    private val satelliteId = "cat_satellite"

    private fun data(): PortfolioData {
        val base = portfolio(
            instruments = listOf(
                cashInstrument(CASH_CNY, "CNY"),
                quotedInstrument(hkStock, satelliteId, currency = "HKD", code = "01810"),
            ),
        )
        return base.copy(
            settings = base.settings.copy(
                categories = base.settings.categories + Category(
                    id = satelliteId,
                    name = "卫星",
                    order = 9,
                    color = "#123456",
                    targetPct = bd("0"),
                    minPct = bd("0"),
                    maxPct = bd("0"),
                    specialRule = "satellite",
                ),
            ),
        )
    }

    private val positions = mapOf(
        PositionKey(INST_A, CASH_CNY) to bd("100"),
        PositionKey(INST_A, hkStock) to bd("10"),
    )

    private fun provider() = MapPriceProvider(
        prices = mapOf(hkStock to bd("100")),
        fxRates = mapOf("HKD" to bd("0.9")),
    )

    @Test
    fun satelliteCountsInTotalButStaysOutOfTargetBases() {
        val snapshot = SnapshotCalculator.build(data(), positions, provider(), date)

        // Total assets include the satellite position (10 * 100 HKD * 0.9).
        assertDecimal("1000", snapshot.totalValueCny)
        assertDecimal("900", snapshot.satelliteValueCny)
        assertDecimal("100", snapshot.strategyValueCny)
        assertDecimal("100", snapshot.portfolioValueCny)

        val satellite = snapshot.categories.single { it.isSatellite }
        assertDecimal("0.9", satellite.share)
        assertNull(satellite.targetValueCny)
        assertNull(satellite.minValueCny)
        assertNull(satellite.maxValueCny)
        assertNull(satellite.gapToTargetCny)
        assertTrue(satellite.inRange)

        // The seven strategy categories are measured against strategy assets.
        val cash = snapshot.categories.single { it.category.id == CategoryIds.CASH }
        assertDecimal("1", cash.share)

        // Dividend target is 32% of strategy assets (100), not total assets:
        // dynAmount(0.32, 100, 0) = 32 * 100 / 68 = 47.06.
        val dividend = snapshot.categories.single { it.category.id == CategoryIds.DIVIDEND }
        assertDecimal("47.06", dividend.targetValueCny)
    }

    @Test
    fun summarySharesDivideTotalAssetsIncludingSatellite() {
        val snapshot = SnapshotCalculator.build(data(), positions, provider(), date)

        // The summary card shares use total assets (1000 CNY) as the
        // denominator, so dividend + portfolio + satellite = 100%:
        // portfolio = 100 CNY cash, satellite = 900 CNY, dividend = 0.
        assertDecimal("0", snapshot.dividendShare)
        assertDecimal("0.1", snapshot.portfolioShare)
        val satellite = snapshot.categories.single { it.isSatellite }
        assertDecimal("0.9", satellite.share)
    }

    @Test
    fun withoutSatelliteTheBasesAreUnchanged() {
        val noSatellite = data().copy(
            settings = data().settings.copy(
                categories = data().settings.categories.filterNot { it.isSatelliteRule() },
            ),
        )
        val snapshot = SnapshotCalculator.build(noSatellite, positions, provider(), date)
        assertDecimal("1000", snapshot.totalValueCny)
        assertDecimal("0", snapshot.satelliteValueCny)
        assertDecimal("1000", snapshot.strategyValueCny)
    }

    private fun Category.isSatelliteRule(): Boolean = specialRule == "satellite"
}
