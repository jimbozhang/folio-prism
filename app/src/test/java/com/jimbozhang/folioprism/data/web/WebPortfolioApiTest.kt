package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.data.store.PortfolioRepository
import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.CASH_USD
import com.jimbozhang.folioprism.domain.FUND
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.INST_B
import com.jimbozhang.folioprism.domain.assertDecimal
import com.jimbozhang.folioprism.domain.cashInstrument
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.quotedInstrument
import com.jimbozhang.folioprism.domain.ref
import com.jimbozhang.folioprism.domain.history.CategoryPnl
import com.jimbozhang.folioprism.domain.history.DailyPortfolioValue
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.ExternalClassification
import com.jimbozhang.folioprism.domain.model.ExternalDirection
import com.jimbozhang.folioprism.domain.model.ExternalEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.money.bd
import com.jimbozhang.folioprism.domain.quote.Quote
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPortfolioApiTest {

    private val today = LocalDate.of(2026, 11, 19)
    private val fund = quotedInstrument(FUND, CategoryIds.GROWTH, "CNY", "000001")
    private val cash = cashInstrument(CASH_CNY, "CNY")
    private val usdCash = cashInstrument(CASH_USD, "USD")
    private val quotes = QuoteSnapshot(
        quotes = mapOf(FUND to Quote(FUND, bd("2"), "CNY", fund.name, "", "test")),
        fxRates = mapOf("USD" to bd("7.2")),
    )

    private val tempDirs = mutableListOf<File>()

    @After
    fun tearDown() {
        tempDirs.forEach { it.deleteRecursively() }
    }

    @Test
    fun overviewComputesSnapshot() {
        val env = environment()
        val overview = runBlocking { env.api.overview() }

        assertDecimal("120", overview.totalValueCny.toBigDecimal())
        assertDecimal("120", overview.portfolioValueCny.toBigDecimal())
        assertFalse(overview.hasMissingPrices)
        assertEquals(2, overview.counts.events)

        val fundPosition = overview.positions.first { it.instrumentId == FUND }
        assertDecimal("20", fundPosition.valueCny!!.toBigDecimal())
        assertDecimal("2", fundPosition.price!!.toBigDecimal())
        assertEquals("quoted", fundPosition.kind)

        val cashCategory = overview.categories.first { it.id == CategoryIds.CASH }
        assertDecimal("100", cashCategory.valueCny.toBigDecimal())
        val growthCategory = overview.categories.first { it.id == CategoryIds.GROWTH }
        assertDecimal("20", growthCategory.valueCny.toBigDecimal())
        assertTrue(overview.categories.all { it.color.isNotBlank() })
        assertEquals(1, overview.institutions.size)
        assertEquals("2026-11-19T00:00:00+08:00", overview.quote.fetchedAt)
    }

    @Test
    fun portfolioReturnsStoredFile() {
        val env = environment()
        val text = runBlocking { env.api.portfolio() }
        assertTrue(text!!.contains("\"schemaVersion\""))
        assertTrue(text.contains(FUND))
    }

    @Test
    fun createUpdateDeleteExpense() {
        val env = environment()
        val created = runBlocking { env.api.createEvent(body(expenseDraft("15"))) }
        assertTrue(created is WriteResult.Ok)
        assertEquals(3, env.repository.data.value!!.events.size)
        val expense = env.repository.data.value!!.events.filterIsInstance<ExpenseEvent>().single()
        assertDecimal("15", expense.amount)
        val id = expense.id

        val updated = runBlocking { env.api.updateEvent(id, body(expenseDraft("20"))) }
        assertTrue(updated is WriteResult.Ok)
        val expenses = env.repository.data.value!!.events.filterIsInstance<ExpenseEvent>()
        assertEquals(1, expenses.size)
        assertEquals(id, expenses.single().id)
        assertDecimal("20", expenses.single().amount)

        val deleted = runBlocking { env.api.deleteEvent(id) }
        assertTrue(deleted is WriteResult.Ok)
        assertEquals(2, env.repository.data.value!!.events.size)
    }

    @Test
    fun deleteIncomeRemovesLinkedExternal() {
        val env = environment()
        val draft = WebEventDraft(
            type = "income",
            date = "2026-11-19 10:00",
            incomeKind = "interest",
            incomeHasSource = false,
            incomeInstitutionId = INST_A,
            incomeInstrumentId = CASH_CNY,
            incomeAmount = "5",
            immediateWithdrawal = true,
        )
        val created = runBlocking { env.api.createEvent(body(draft)) }
        assertTrue(created is WriteResult.Ok)
        assertEquals(4, env.repository.data.value!!.events.size)

        val income = env.repository.data.value!!.events.filterIsInstance<IncomeEvent>().single()
        val deleted = runBlocking { env.api.deleteEvent(income.id) }
        assertTrue(deleted is WriteResult.Ok)
        assertEquals(2, env.repository.data.value!!.events.size)
    }

    @Test
    fun invalidAndMissingWritesAreReported() {
        val env = environment()
        val duplicateOpening = WebEventDraft(
            type = "opening",
            date = "2026-11-19 10:00",
            openingInstitutionId = INST_A,
            openingInstrumentId = CASH_CNY,
            openingValue = "50",
        )
        val invalid = runBlocking { env.api.createEvent(body(duplicateOpening)) }
        assertTrue(invalid is WriteResult.Invalid)
        assertTrue((invalid as WriteResult.Invalid).message.contains("期初"))

        val missingUpdate = runBlocking { env.api.updateEvent("missing", body(expenseDraft("1"))) }
        assertEquals(WriteResult.NotFound, missingUpdate)
        assertEquals(WriteResult.NotFound, runBlocking { env.api.deleteEvent("missing") })
    }

    @Test
    fun flowsAndExpensesAggregateByMonth() {
        val external = ExternalEvent(
            direction = ExternalDirection.IN,
            classification = ExternalClassification.CAPITAL,
            position = ref(INST_A, CASH_CNY),
            amount = bd("100"),
            currency = "CNY",
            date = "2026-02-10T00:00:00+08:00",
        )
        val expense = ExpenseEvent(
            amount = bd("30"),
            currency = "CNY",
            categoryId = CategoryIds.CASH,
            date = "2026-02-11T00:00:00+08:00",
        )
        val env = environment(baseData(external, expense))

        val flows = runBlocking { env.api.flows(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)) }
        assertEquals(1, flows.months.size)
        assertEquals("2026-02", flows.months[0].month)
        assertDecimal("100", flows.months[0].inflowCny.toBigDecimal())
        assertDecimal("0", flows.months[0].outflowCny.toBigDecimal())

        val expenses = runBlocking { env.api.expenses(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)) }
        assertEquals(1, expenses.months.size)
        assertDecimal("30", expenses.months[0].totalCny.toBigDecimal())
    }

    @Test
    fun chartsReturnSeriesAndSummaries() {
        val env = environment()

        val assets = runBlocking { env.api.chart("assets", LocalDate.of(2026, 1, 1), today, null) }
        assertEquals(2, assets.points.size)
        assertEquals("120", assets.points.first().value)
        assertEquals("130", assets.points.last().value)
        assertEquals("130", assets.summary!!.endValue)
        assertEquals("10", assets.summary!!.totalPnl)

        val categoryAssets = runBlocking {
            env.api.chart("category_assets", LocalDate.of(2026, 1, 1), today, CategoryIds.GROWTH)
        }
        assertEquals("20", categoryAssets.points.first().value)
        assertEquals("30", categoryAssets.points.last().value)
        assertEquals("成长", categoryAssets.category!!.name)

        val pnl = runBlocking { env.api.chart("category_pnl", LocalDate.of(2026, 1, 1), today, null) }
        assertEquals(2, pnl.categoryPnl.size)
        assertTrue(pnl.categoryPnl.all { it.color.isNotBlank() })
        assertEquals("10", pnl.summary!!.totalPnl)

        val badRange = runCatching {
            runBlocking { env.api.chart("assets", today, LocalDate.of(2026, 1, 1), null) }
        }
        assertTrue(badRange.isFailure)

        val unknownType = runCatching {
            runBlocking { env.api.chart("nope", LocalDate.of(2026, 1, 1), today, null) }
        }
        assertTrue(unknownType.isFailure)
    }

    @Test
    fun refreshQuotesReturnsStatus() {
        val env = environment()
        val status = runBlocking { env.api.refreshQuotes() }
        assertEquals("2026-11-19T00:00:00+08:00", status.fetchedAt)
        assertTrue(status.failures.isEmpty())
    }

    private fun body(draft: WebEventDraft): String = WEB_JSON.encodeToString(draft)

    private fun expenseDraft(amount: String): WebEventDraft = WebEventDraft(
        type = "expense",
        date = "2026-11-19 10:00",
        expenseNoPosition = true,
        expenseAmount = amount,
        expenseCategoryId = CategoryIds.CASH,
    )

    private fun baseData(vararg extra: Event): PortfolioData = portfolio(
        institutions = listOf(Institution(INST_A, "机构A"), Institution(INST_B, "机构B")),
        instruments = listOf(fund, cash, usdCash),
        events = listOf(
            opening(ref(INST_A, FUND), quantity = "10"),
            opening(ref(INST_A, CASH_CNY), amount = "100"),
        ) + extra,
    )

    private fun environment(
        initial: PortfolioData = baseData(),
        history: HistorySource = FixedHistory(historyResult()),
    ): Env {
        val dir = Files.createTempDirectory("web-api").toFile()
        tempDirs += dir
        val dataFile = File(dir, "folio-prism.json")
        val repository = PortfolioRepository(dataFile, File(dir, "backups"), Dispatchers.Unconfined)
        runBlocking { repository.save(initial) }
        val api = WebPortfolioApi(
            repository = repository,
            dataFile = dataFile,
            quotes = FixedQuotes(quotes),
            history = history,
            today = { today },
        )
        return Env(repository, api)
    }

    private fun historyResult(): HistoryResult = HistoryResult(
        from = LocalDate.of(2026, 1, 1),
        to = today,
        curve = listOf(
            DailyPortfolioValue(
                date = LocalDate.of(2026, 1, 1),
                totalCny = bd("120"),
                categoryValues = mapOf(CategoryIds.CASH to bd("100"), CategoryIds.GROWTH to bd("20")),
                incomplete = false,
            ),
            DailyPortfolioValue(
                date = today,
                totalCny = bd("130"),
                categoryValues = mapOf(CategoryIds.CASH to bd("100"), CategoryIds.GROWTH to bd("30")),
                incomplete = false,
            ),
        ),
        categoryPnl = listOf(
            CategoryPnl(CategoryIds.CASH, "现金", bd("0"), false),
            CategoryPnl(CategoryIds.GROWTH, "成长", bd("10"), false),
        ),
        logs = listOf("history ok"),
    )

    private class Env(
        val repository: PortfolioRepository,
        val api: WebPortfolioApi,
    )

    private class FixedQuotes(private val snapshot: QuoteSnapshot) : QuoteSource {
        override suspend fun ensure(data: PortfolioData): QuoteSnapshot = snapshot
        override suspend fun refresh(data: PortfolioData): QuoteSnapshot = snapshot
        override fun fetchedAt(): String = "2026-11-19T00:00:00+08:00"
    }

    private class FixedHistory(private val result: HistoryResult) : HistorySource {
        override suspend fun load(
            data: PortfolioData,
            from: LocalDate,
            to: LocalDate,
            quotes: QuoteSnapshot,
        ): HistoryResult = result
    }
}
