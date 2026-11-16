package com.jimbozhang.folioprism.data.json

import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Meta
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.Settings
import com.jimbozhang.folioprism.domain.money.bd
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

object DefaultData {

    private const val PCT_SCALE = 10
    private val SIX = BigDecimal(6)

    fun empty(now: String = OffsetDateTime.now().toString()): PortfolioData =
        PortfolioData(
            meta = Meta(createdAt = now, updatedAt = now),
            settings = Settings(categories = defaultCategories()),
        )

    /**
     * Defaults applied only when the user JSON file does not exist:
     * dividend targets 32% of total (±10% band); each of the other six
     * categories targets 1/6 of the non-dividend base, with a ±10% band.
     * The JSON's `settings.categories` always takes precedence.
     */
    fun defaultCategories(): List<Category> = listOf(
        Category(
            id = CategoryIds.DIVIDEND,
            name = "红利",
            order = 1,
            color = "#ff7675",
            targetPct = bd("32"),
            minPct = bd("28.8"),
            maxPct = bd("35.2"),
            specialRule = "dividend",
        ),
        remainingCategory(CategoryIds.GROWTH, "成长", 2, "#e17055"),
        remainingCategory(CategoryIds.US_EQUITY, "美股", 3, "#f9a825"),
        remainingCategory(CategoryIds.US_BOND, "美债", 4, "#0984e3"),
        remainingCategory(CategoryIds.CN_BOND, "中债", 5, "#6c5ce7"),
        remainingCategory(CategoryIds.GOLD, "黄金", 6, "#f9ca24"),
        remainingCategory(CategoryIds.CASH, "现金", 7, "#b2bec3"),
    )

    private fun remainingCategory(
        id: String,
        name: String,
        order: Int,
        color: String,
    ): Category = Category(
        id = id,
        name = name,
        order = order,
        color = color,
        targetPct = remainingPct(100),
        minPct = remainingPct(90),
        maxPct = remainingPct(110),
    )

    /** Percent of the non-dividend base: 100/6 ≈ 16.6667, 90/6 = 15, 110/6 ≈ 18.3333. */
    private fun remainingPct(numerator: Int): BigDecimal =
        BigDecimal(numerator).divide(SIX, PCT_SCALE, RoundingMode.HALF_UP)
}
