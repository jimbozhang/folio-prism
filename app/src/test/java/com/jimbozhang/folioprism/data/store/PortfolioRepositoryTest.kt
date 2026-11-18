package com.jimbozhang.folioprism.data.store

import com.jimbozhang.folioprism.data.json.PortfolioJson
import com.jimbozhang.folioprism.domain.CASH_CNY
import com.jimbozhang.folioprism.domain.GROWTH
import com.jimbozhang.folioprism.domain.INST_A
import com.jimbozhang.folioprism.domain.opening
import com.jimbozhang.folioprism.domain.portfolio
import com.jimbozhang.folioprism.domain.ref
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PortfolioRepositoryTest {

    /**
     * The file is rewritten in full on every save, so the stored ledger is
     * kept in chronological order: events supplied out of order must be
     * persisted sorted, and a second save keeps them sorted.
     */
    @Test
    fun savePersistsEventsInChronologicalOrder() = runBlocking {
        val dir = Files.createTempDirectory("portfolio-repo").toFile()
        val dataFile = File(dir, "folio-prism.json")
        val repo = PortfolioRepository(dataFile, File(dir, "backups"))

        val early = opening(
            position = ref(INST_A, CASH_CNY),
            amount = "1",
            date = "2026-01-01T00:00:00+08:00",
        )
        val mid = opening(
            position = ref(INST_A, GROWTH),
            quantity = "5",
            date = "2026-02-01T00:00:00+08:00",
        )
        val late = opening(
            position = ref(INST_A, CASH_CNY),
            amount = "3",
            date = "2026-03-01T00:00:00+08:00",
        )

        val saved = repo.save(portfolio(events = listOf(late, mid, early)))

        val expectedIds = listOf(early, mid, late).map { it.id }
        assertEquals(expectedIds, saved.events.map { it.id })
        assertEquals(expectedIds, PortfolioJson.load(dataFile).events.map { it.id })

        // A second save of the same content keeps the order (stable sort).
        val resaved = repo.save(saved)
        assertEquals(expectedIds, resaved.events.map { it.id })

        dir.deleteRecursively()
        Unit
    }
}
