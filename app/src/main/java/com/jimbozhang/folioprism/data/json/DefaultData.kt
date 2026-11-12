package com.jimbozhang.folioprism.data.json

import com.jimbozhang.folioprism.domain.model.Category
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Meta
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.Settings
import com.jimbozhang.folioprism.domain.money.bd
import java.time.OffsetDateTime

object DefaultData {

    fun empty(now: String = OffsetDateTime.now().toString()): PortfolioData =
        PortfolioData(
            meta = Meta(createdAt = now, updatedAt = now),
            settings = Settings(categories = defaultCategories()),
        )

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
        Category(
            id = CategoryIds.GROWTH,
            name = "成长",
            order = 2,
            color = "#e17055",
            targetPct = bd("24"),
            minPct = bd("21.6"),
            maxPct = bd("26.4"),
        ),
        Category(
            id = CategoryIds.US_EQUITY,
            name = "美股",
            order = 3,
            color = "#f9a825",
            targetPct = bd("22"),
            minPct = bd("18"),
            maxPct = bd("24.2"),
        ),
        Category(
            id = CategoryIds.US_BOND,
            name = "美债",
            order = 4,
            color = "#0984e3",
            targetPct = bd("4"),
            minPct = bd("1.2"),
            maxPct = bd("5"),
        ),
        Category(
            id = CategoryIds.CN_BOND,
            name = "中债",
            order = 5,
            color = "#6c5ce7",
            targetPct = bd("8"),
            minPct = bd("6"),
            maxPct = bd("10"),
        ),
        Category(
            id = CategoryIds.GOLD,
            name = "黄金",
            order = 6,
            color = "#f9ca24",
            targetPct = bd("30"),
            minPct = bd("27"),
            maxPct = bd("33"),
        ),
        Category(
            id = CategoryIds.CASH,
            name = "现金",
            order = 7,
            color = "#b2bec3",
            targetPct = bd("12"),
            minPct = bd("2"),
            maxPct = bd("22"),
        ),
    )
}
