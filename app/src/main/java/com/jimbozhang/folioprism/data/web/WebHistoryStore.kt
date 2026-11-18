package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.data.history.HistoricalPriceService
import com.jimbozhang.folioprism.data.history.PriceCacheRepository
import com.jimbozhang.folioprism.data.price.HttpFetcher
import com.jimbozhang.folioprism.domain.calc.HistoryCalculator
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * History loading for the web module, reusing the app's price cache and its
 * persisted fetch backoff. Only the latest computed result is memoized.
 */
class WebHistoryStore(
    fetcher: HttpFetcher,
    cacheFile: File,
) : HistorySource {
    private val service = HistoricalPriceService(fetcher, PriceCacheRepository(cacheFile))
    private val mutex = Mutex()

    private var cachedKey: String? = null
    private var cached: HistoryResult? = null

    override suspend fun load(
        data: PortfolioData,
        from: LocalDate,
        to: LocalDate,
        quotes: QuoteSnapshot,
    ): HistoryResult = mutex.withLock {
        val key = cacheKey(data, from, to, quotes)
        val hit = cached
        if (hit != null && cachedKey == key) return hit

        val fetched = service.fetchAndCache(data, from, to)
        val calculated = HistoryCalculator.build(
            data = data,
            cache = fetched.cache,
            currentFxRates = quotes.fxRates,
            from = from,
            to = to,
            currentPrices = quotes.quotes.mapValues { it.value.price },
        )
        val result = calculated.copy(logs = fetched.logs + calculated.logs)
        cached = result
        cachedKey = key
        result
    }

    fun clear() {
        cached = null
        cachedKey = null
    }

    private fun cacheKey(
        data: PortfolioData,
        from: LocalDate,
        to: LocalDate,
        quotes: QuoteSnapshot,
    ): String = buildString {
        append(data.meta.updatedAt).append('|')
        append(from).append("..").append(to).append('|')
        quotes.quotes.toSortedMap().forEach { (id, quote) ->
            append(id).append('=').append(quote.price).append(';')
        }
        append('|')
        quotes.fxRates.toSortedMap().forEach { (currency, rate) ->
            append(currency).append('=').append(rate).append(';')
        }
    }
}
