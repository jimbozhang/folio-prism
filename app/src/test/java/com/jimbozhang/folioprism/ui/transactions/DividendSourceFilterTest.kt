package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import org.junit.Assert.assertEquals
import org.junit.Test

class DividendSourceFilterTest {

    @Test
    fun includesDividendAndUsdEquityBondOnly() {
        val dividend = quotedInstrument("ins_div", CategoryIds.DIVIDEND, "CNY", "008163")
        val usEquityUsd = quotedInstrument("ins_voo", CategoryIds.US_EQUITY, "USD", "VOO")
        val usBondUsd = quotedInstrument("ins_tlt", CategoryIds.US_BOND, "USD", "TLT")
        val usEquityCny = quotedInstrument("ins_513500", CategoryIds.US_EQUITY, "CNY", "513500")
        val usBondCny = quotedInstrument("ins_002286", CategoryIds.US_BOND, "CNY", "002286")
        val growth = quotedInstrument("ins_growth", CategoryIds.GROWTH, "CNY", "007028")
        val cash = cashInstrument("ins_cash", "CNY")
        val data = portfolio(
            instruments = listOf(
                dividend,
                usEquityUsd,
                usBondUsd,
                usEquityCny,
                usBondCny,
                growth,
                cash,
            ),
        )

        assertEquals(
            setOf("ins_div", "ins_voo", "ins_tlt"),
            dividendSourceInstrumentIds(data),
        )
    }
}
