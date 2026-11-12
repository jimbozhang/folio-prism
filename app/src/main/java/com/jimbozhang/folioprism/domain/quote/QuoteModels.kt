package com.jimbozhang.folioprism.domain.quote

import java.math.BigDecimal

data class Quote(
    val instrumentId: String,
    val price: BigDecimal,
    val currency: String,
    val name: String,
    val updateTime: String,
    val source: String,
)

enum class QuoteFailureKind {
    PRICE,
    FX,
    GOLD,
    MANUAL_PRICE_MISSING,
    UNKNOWN_SOURCE,
}

data class QuoteFailure(
    val instrumentId: String?,
    val instrumentCode: String?,
    val instrumentName: String?,
    val institutionIds: List<String>,
    val kind: QuoteFailureKind,
    val source: String,
    val message: String,
    val timestamp: String,
)

data class QuoteSnapshot(
    val quotes: Map<String, Quote> = emptyMap(),
    val fxRates: Map<String, BigDecimal> = emptyMap(),
    val failures: List<QuoteFailure> = emptyList(),
    val logs: List<String> = emptyList(),
)
