package com.jimbozhang.folioprism.ui.components

import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.calc.PositionKey
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.money.bd
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class PositionPickerFilterTest {

    @Test
    fun keepsOnlyInstitutionsHoldingEligibleAssets() {
        val a = Institution(INST_A, "A")
        val b = Institution(INST_B, "B")
        val c = Institution("inst_c", "C")
        val quantities = mapOf(
            PositionKey(INST_A, "ins_div") to bd("100"),
            PositionKey(INST_B, "ins_div") to BigDecimal.ZERO,
            PositionKey(INST_B, "ins_growth") to bd("100"),
            PositionKey("inst_c", "ins_voo") to bd("5"),
        )

        assertEquals(
            listOf(a, c),
            institutionsHoldingAny(listOf(a, b, c), setOf("ins_div", "ins_voo"), quantities),
        )
    }

    @Test
    fun emptyWhenNoEligibleHoldings() {
        val a = Institution(INST_A, "A")
        val quantities = mapOf(PositionKey(INST_A, "ins_growth") to bd("100"))

        assertEquals(
            emptyList<Institution>(),
            institutionsHoldingAny(listOf(a), setOf("ins_div"), quantities),
        )
    }
}
