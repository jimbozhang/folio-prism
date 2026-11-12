package com.jimbozhang.folioprism.ui.transactions

import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.DATE1
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.domain.ref
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningValidationTest {

    private val existing = opening(ref(INST_A, CASH_CNY), amount = "100", date = DATE1)

    private fun data() = portfolio(
        instruments = listOf(cashInstrument(CASH_CNY, "CNY")),
        events = listOf(existing),
    )

    private fun newForm(): EventForm = EventForm(null, data()).apply {
        type = "opening"
        openingInstitutionId = INST_A
        openingInstrumentId = CASH_CNY
        openingValue = "50"
        openingFx = "1"
    }

    @Test
    fun secondOpeningForSamePositionIsRejected() {
        val result = buildEvents(data(), QuoteSnapshot(), newForm(), null)

        assertTrue(result.isFailure)
        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("2026-01-01"))
        assertTrue(message.contains("期初"))
    }

    @Test
    fun editingExistingOpeningIsAllowed() {
        val form = EventForm(existing, data()).apply { openingValue = "120" }

        val result = buildEvents(data(), QuoteSnapshot(), form, existing)

        assertTrue(result.isSuccess)
    }

    @Test
    fun openingForDifferentPositionIsAllowed() {
        val form = newForm().apply { openingInstitutionId = INST_B }

        val result = buildEvents(data(), QuoteSnapshot(), form, null)

        assertTrue(result.isSuccess)
    }
}
