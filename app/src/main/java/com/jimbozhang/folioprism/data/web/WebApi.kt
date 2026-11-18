package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.time.LocalDate

sealed interface WriteResult {
    data class Ok(val version: String) : WriteResult
    data class Invalid(val message: String) : WriteResult
    data object NotFound : WriteResult
    data class Failed(val message: String) : WriteResult
}

/**
 * Everything the HTTP layer can ask for. Implemented by [WebPortfolioApi] in
 * production and by fakes in unit tests, so the router stays testable without
 * Android or the network.
 */
interface WebApi {
    /** Raw data file text, or null when it does not exist. */
    suspend fun portfolio(): String?

    suspend fun overview(): OverviewDto

    suspend fun chart(
        type: String,
        from: LocalDate,
        to: LocalDate,
        categoryId: String?,
    ): ChartDto

    suspend fun flows(from: LocalDate, to: LocalDate): FlowsDto

    suspend fun expenses(from: LocalDate, to: LocalDate): ExpensesDto

    suspend fun refreshQuotes(): QuoteStatusDto

    suspend fun createEvent(body: String): WriteResult

    suspend fun updateEvent(id: String, body: String): WriteResult

    suspend fun deleteEvent(id: String): WriteResult
}

/** Quote access from the API's point of view; faked in tests. */
interface QuoteSource {
    suspend fun ensure(data: PortfolioData): QuoteSnapshot

    suspend fun refresh(data: PortfolioData): QuoteSnapshot

    fun fetchedAt(): String?
}

/** History access from the API's point of view; faked in tests. */
interface HistorySource {
    suspend fun load(
        data: PortfolioData,
        from: LocalDate,
        to: LocalDate,
        quotes: QuoteSnapshot,
    ): HistoryResult
}
