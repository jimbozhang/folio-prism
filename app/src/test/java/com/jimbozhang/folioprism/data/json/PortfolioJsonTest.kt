package com.jimbozhang.folioprism.data.json

import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.Meta
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.Settings
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortfolioJsonTest {

    private fun portfolioWith(event: ExpenseEvent): PortfolioData = PortfolioData(
        meta = Meta(),
        settings = Settings(categories = DefaultData.defaultCategories()),
        institutions = emptyList(),
        instruments = emptyList(),
        events = listOf(event),
    )

    private fun expense() = ExpenseEvent(
        amount = BigDecimal("12.30"),
        currency = "CNY",
        categoryId = "cat_cash",
        date = "2026-11-23 11:21",
        id = "evt_test",
        position = PositionRef("inst_a", "ins_cash"),
    )

    /** Recording timestamps are not part of the data model any more. */
    @Test
    fun eventsAreEncodedWithoutCreatedAt() {
        val text = PortfolioJson.encode(portfolioWith(expense()))

        assertTrue(text.contains("\"evt_test\""))
        assertFalse(text.contains("\"createdAt\""))
    }

    /** Files written by older versions still decode; the value is ignored. */
    @Test
    fun legacyFilesWithCreatedAtStillDecode() {
        val legacy = PortfolioJson.encode(portfolioWith(expense())).replace(
            "\"id\": \"evt_test\"",
            "\"id\": \"evt_test\",\n      \"createdAt\": \"2026-11-23T12:00:00+08:00\"",
        )
        assertTrue(legacy.contains("createdAt"))

        val decoded = PortfolioJson.decode(legacy)

        assertEquals(1, decoded.events.size)
        assertEquals("evt_test", decoded.events.first().id)
        // The legacy value is dropped on load, so the next save removes the
        // field from every event, not only the edited ones.
        assertEquals("", decoded.events.first().createdAt)
    }

    @Test
    fun decodingAppliesBuiltInCategoryNames() {
        val data = portfolioWith(expense()).copy(
            settings = Settings(
                categories = DefaultData.defaultCategories().map {
                    if (it.id == CategoryIds.GROWTH) it.copy(name = "自定义成长") else it
                },
            ),
        )

        val decoded = PortfolioJson.decode(PortfolioJson.encode(data))

        assertEquals(
            "成长",
            decoded.settings.categories.first { it.id == CategoryIds.GROWTH }.name,
        )
    }
}
