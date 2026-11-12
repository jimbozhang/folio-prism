package com.jimbozhang.folioprism.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jimbozhang.folioprism.data.history.HistoricalPriceService
import com.jimbozhang.folioprism.data.history.PriceCacheRepository
import com.jimbozhang.folioprism.data.price.OkHttpFetcher
import com.jimbozhang.folioprism.data.price.QuoteService
import com.jimbozhang.folioprism.data.store.PortfolioRepository
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.HistoryCalculator
import com.jimbozhang.folioprism.domain.history.HistoryResult
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.quote.QuoteFailure
import com.jimbozhang.folioprism.domain.quote.QuoteFailureKind
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import com.jimbozhang.folioprism.platform.storage.StoragePaths
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AppUiState {
    data object Loading : AppUiState
    data object LoadingQuotes : AppUiState
    data class Ready(
        val data: PortfolioData,
        val quotes: QuoteSnapshot,
        val refreshing: Boolean = false,
    ) : AppUiState
    data class QuoteFailed(
        val data: PortfolioData,
        val failures: List<QuoteFailure>,
        val logs: List<String>,
    ) : AppUiState
    data class Error(val message: String) : AppUiState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val fetcher = OkHttpFetcher()
    private val repository = PortfolioRepository(
        dataFile = StoragePaths.dataFile(),
        backupDir = StoragePaths.backupDir(),
    )
    private val priceCacheRepository = PriceCacheRepository(StoragePaths.priceCacheFile())
    private val quoteService = QuoteService(fetcher)
    private val historicalService = HistoricalPriceService(fetcher, priceCacheRepository)

    private val _state = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private val _historyState = MutableStateFlow<HistoryUiState>(HistoryUiState.Idle)
    val historyState: StateFlow<HistoryUiState> = _historyState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = AppUiState.Loading
            val data = runCatching { repository.load() }.getOrElse { t ->
                _state.value = AppUiState.Error(t.message ?: t::class.java.simpleName)
                return@launch
            }
            _state.value = AppUiState.LoadingQuotes
            fetchQuotes(data)
        }
    }

    fun refreshQuotes() {
        viewModelScope.launch {
            val current = _state.value
            if (current is AppUiState.Ready && current.refreshing) return@launch
            val data = currentData() ?: return@launch
            if (current is AppUiState.Ready) {
                _state.value = current.copy(refreshing = true)
            } else {
                _state.value = AppUiState.LoadingQuotes
            }
            fetchQuotes(data)
        }
    }

    fun updateData(transform: (PortfolioData) -> PortfolioData) {
        viewModelScope.launch {
            val updated = runCatching { repository.update(transform) }.getOrElse {
                _state.value = AppUiState.Error(it.message ?: it::class.java.simpleName)
                return@launch
            }
            _historyState.value = HistoryUiState.Idle
            _state.value = AppUiState.LoadingQuotes
            fetchQuotes(updated)
        }
    }

    fun loadHistory(from: LocalDate, to: LocalDate) {
        viewModelScope.launch {
            val data = currentData() ?: return@launch
            _historyState.value = HistoryUiState.Loading
            val startedAt = System.currentTimeMillis()
            val result = runCatching {
                val fetched = historicalService.fetchAndCache(data, from, to)
                val fetchedAt = System.currentTimeMillis()
                val calculated = HistoryCalculator.build(data, fetched.cache, from, to)
                val calculatedAt = System.currentTimeMillis()
                android.util.Log.d(
                    "FolioPrismHistory",
                    "range=$from..$to fetch=${fetchedAt - startedAt}ms " +
                        "calc=${calculatedAt - fetchedAt}ms total=${calculatedAt - startedAt}ms",
                )
                calculated.copy(logs = fetched.logs + calculated.logs)
            }
            _historyState.value = result.fold(
                onSuccess = { HistoryUiState.Ready(it) },
                onFailure = { HistoryUiState.Error(it.message ?: "历史行情计算失败") },
            )
        }
    }

    fun setManualPrice(instrumentId: String, price: BigDecimal) {
        viewModelScope.launch {
            val data = currentData() ?: return@launch
            val updated = data.copy(
                instruments = data.instruments.map { instrument ->
                    if (instrument.id == instrumentId) {
                        instrument.copy(
                            quote = instrument.quote.copy(
                                mode = QuoteMode.MANUAL,
                                manualPrice = price,
                            ),
                        )
                    } else {
                        instrument
                    }
                },
            )
            runCatching { repository.save(updated) }
                .onSuccess {
                    _state.value = AppUiState.LoadingQuotes
                    fetchQuotes(it)
                }
                .onFailure {
                    _state.value = AppUiState.Error(it.message ?: it::class.java.simpleName)
                }
        }
    }

    fun saveLog(onResult: (Result<String>) -> Unit) {
        val logs = when (val state = _state.value) {
            is AppUiState.QuoteFailed -> state.logs
            else -> emptyList()
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    StoragePaths.logDir().mkdirs()
                    val stamp = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd'T'HH-mm-ss")
                        .format(java.time.LocalDateTime.now())
                    val file = File(StoragePaths.logDir(), "folio-prism-$stamp.log")
                    file.writeText(
                        logs.joinToString("\n").ifBlank { "no entries" },
                        Charsets.UTF_8,
                    )
                    file.absolutePath
                }
            }
            onResult(result)
        }
    }

    private suspend fun fetchQuotes(data: PortfolioData) {
        val positions = EventReplay.replay(data.events)
        val snapshot = runCatching {
            quoteService.fetch(data, positions)
        }.getOrElse { t ->
            QuoteSnapshot(
                failures = listOf(
                    QuoteFailure(
                        instrumentId = null,
                        instrumentCode = null,
                        instrumentName = null,
                        institutionIds = emptyList(),
                        kind = QuoteFailureKind.PRICE,
                        source = "quote-service",
                        message = t.message ?: "行情服务异常",
                        timestamp = OffsetDateTime.now().toString(),
                    ),
                ),
                logs = listOf("quote service failed: ${t.message}"),
            )
        }
        _state.value = if (snapshot.failures.isEmpty()) {
            AppUiState.Ready(data, snapshot)
        } else {
            AppUiState.QuoteFailed(data, snapshot.failures, snapshot.logs)
        }
    }

    private fun currentData(): PortfolioData? = when (val state = _state.value) {
        is AppUiState.Ready -> state.data
        is AppUiState.QuoteFailed -> state.data
        else -> repository.data.value
    }
}
