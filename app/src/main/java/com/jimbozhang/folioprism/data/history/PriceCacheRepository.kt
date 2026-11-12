package com.jimbozhang.folioprism.data.history

import com.jimbozhang.folioprism.domain.history.CachedSeries
import com.jimbozhang.folioprism.domain.history.PriceCacheData
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PriceCacheRepository(
    private val file: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        // The cache is machine-managed and may grow large; keep it compact.
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    suspend fun load(): PriceCacheData = withContext(dispatcher) {
        if (!file.exists()) {
            PriceCacheData()
        } else {
            runCatching {
                json.decodeFromString<PriceCacheData>(file.readText(Charsets.UTF_8))
            }.getOrElse { PriceCacheData() }
        }
    }

    suspend fun save(cache: PriceCacheData): PriceCacheData = withContext(dispatcher) {
        val updated = cache.copy(updatedAt = OffsetDateTime.now().toString())
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.encodeToString(updated), Charsets.UTF_8)
        json.decodeFromString<PriceCacheData>(tmp.readText(Charsets.UTF_8))
        if (file.exists() && !file.delete()) error("Unable to replace ${file.path}")
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        updated
    }

    fun mergeSeries(
        cache: PriceCacheData,
        instrumentId: String,
        prices: Map<LocalDate, java.math.BigDecimal>,
        source: String,
        fetchedAt: String = OffsetDateTime.now().toString(),
    ): PriceCacheData {
        if (prices.isEmpty()) return cache
        val existing = cache.quotes[instrumentId] ?: CachedSeries()
        val auto = existing.auto.toMutableMap()
        prices.forEach { (date, value) -> auto[date.toString()] = value.toPlainString() }
        return cache.copy(
            quotes = cache.quotes + (
                instrumentId to existing.copy(
                    auto = auto,
                    lastAutoFetchAt = fetchedAt,
                    source = source,
                )
                ),
        )
    }

    fun mergeFx(
        cache: PriceCacheData,
        currency: String,
        rates: Map<LocalDate, java.math.BigDecimal>,
        source: String,
        fetchedAt: String = OffsetDateTime.now().toString(),
    ): PriceCacheData {
        if (rates.isEmpty()) return cache
        val existing = cache.fx[currency] ?: CachedSeries()
        val auto = existing.auto.toMutableMap()
        rates.forEach { (date, value) -> auto[date.toString()] = value.toPlainString() }
        return cache.copy(
            fx = cache.fx + (
                currency to existing.copy(
                    auto = auto,
                    lastAutoFetchAt = fetchedAt,
                    source = source,
                )
                ),
        )
    }
}
