package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.data.price.HttpFetcher
import com.jimbozhang.folioprism.data.price.QuoteService
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.quote.QuoteFailure
import com.jimbozhang.folioprism.domain.quote.QuoteFailureKind
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.time.OffsetDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Quote cache owned entirely by the web module. The app's UI state is never
 * read or written: the web side fetches once on first use and again only when
 * the user presses "refresh".
 */
class WebQuoteStore(fetcher: HttpFetcher) : QuoteSource {

    private val service = QuoteService(fetcher)
    private val mutex = Mutex()

    @Volatile
    private var snapshot: QuoteSnapshot? = null

    @Volatile
    private var fetchedAt: String? = null

    override suspend fun ensure(data: PortfolioData): QuoteSnapshot = mutex.withLock {
        snapshot ?: fetch(data)
    }

    override suspend fun refresh(data: PortfolioData): QuoteSnapshot = mutex.withLock {
        fetch(data)
    }

    override fun fetchedAt(): String? = fetchedAt

    fun clear() {
        snapshot = null
        fetchedAt = null
    }

    private suspend fun fetch(data: PortfolioData): QuoteSnapshot {
        val fetched = runCatching {
            service.fetch(data, EventReplay.replay(data.events))
        }.getOrElse { t ->
            failedSnapshot(t)
        }
        snapshot = fetched
        fetchedAt = OffsetDateTime.now().toString()
        return fetched
    }

    private fun failedSnapshot(t: Throwable): QuoteSnapshot {
        val message = t.message ?: "行情服务异常"
        return QuoteSnapshot(
            failures = listOf(
                QuoteFailure(
                    instrumentId = null,
                    instrumentCode = null,
                    instrumentName = null,
                    institutionIds = emptyList(),
                    kind = QuoteFailureKind.PRICE,
                    source = "quote-service",
                    message = message,
                    timestamp = OffsetDateTime.now().toString(),
                ),
            ),
            logs = listOf("quote service failed: $message"),
        )
    }
}
