package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.data.json.PortfolioJson
import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.DATE1
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.transfer
import com.jimbozhang.folioprism.domain.model.AdjustmentEvent
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.IncomeKind
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.money.bd
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTest {

    private fun sampleEvents(): List<Event> = listOf(
        opening(ref(INST_A, CASH_CNY), amount = "1000"),
        transfer(
            from = ref(INST_A, CASH_CNY),
            to = ref(INST_A, FUND),
            fromQuantity = "1000",
            toQuantity = "100",
            toPrice = "10",
        ),
        IncomeEvent(
            kind = IncomeKind.DIVIDEND,
            source = ref(INST_A, FUND),
            destination = ref(INST_A, CASH_CNY),
            amount = bd("50"),
            currency = "CNY",
            amountCny = bd("50"),
            date = DATE1,
        ),
        ExpenseEvent(
            amount = bd("10"),
            currency = "CNY",
            amountCny = bd("10"),
            categoryId = CategoryIds.CASH,
            position = ref(INST_A, CASH_CNY),
            date = DATE1,
        ),
        ExternalEvent(
            direction = ExternalDirection.IN,
            classification = ExternalClassification.CAPITAL,
            position = ref(INST_A, CASH_CNY),
            amount = bd("500"),
            currency = "CNY",
            amountCny = bd("500"),
            date = DATE1,
        ),
        AdjustmentEvent(
            position = ref(INST_A, CASH_CNY),
            reason = "reconcile",
            deltaAmount = bd("-1"),
            date = DATE1,
        ),
    )

    private fun sampleData() = portfolio(
        instruments = listOf(
            cashInstrument(CASH_CNY, "CNY"),
            quotedInstrument(FUND, CategoryIds.GROWTH),
        ),
        events = sampleEvents(),
    )

    @Test
    fun roundTripKeepsEventTypesAndDecimals() {
        val data = sampleData()
        val encoded = PortfolioJson.encode(data)
        val decoded = PortfolioJson.decode(encoded)

        assertEquals(data.schemaVersion, decoded.schemaVersion)
        assertEquals(data.events.size, decoded.events.size)
        assertTrue(decoded.events[1] is TransferEvent)
        assertEquals(6, decoded.events.size)
        assertTrue(encoded.contains("\"type\": \"transfer\""))
        assertTrue(encoded.contains("\"fromQuantity\": \"1000\""))
        assertTrue(encoded.contains("\"targetPct\": \"32\""))
    }

    @Test
    fun saveAtomicWritesReadableFile() {
        val dir = Files.createTempDirectory("folio-prism-json").toFile()
        val file = File(dir, "folio-prism.json")
        val data = sampleData()

        PortfolioJson.saveAtomic(file, data)
        val loaded = PortfolioJson.load(file)

        assertEquals(data.events.size, loaded.events.size)
        assertTrue(file.exists())
        assertTrue(file.readText().contains("\"schemaVersion\": 1"))
        dir.deleteRecursively()
    }

    @Test
    fun invalidJsonFails() {
        assertThrows(Exception::class.java) {
            PortfolioJson.decode("{not json}")
        }
    }

}
