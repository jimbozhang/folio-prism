package com.jimbozhang.folioprism.domain.model

import com.jimbozhang.folioprism.domain.money.BigDecimalAsStringSerializer
import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Meta(
    val createdAt: String = "",
    val updatedAt: String = "",
    val appVersion: String = "0.1.0",
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val order: Int,
    val color: String,
    @Serializable(with = BigDecimalAsStringSerializer::class) val targetPct: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val minPct: BigDecimal,
    @Serializable(with = BigDecimalAsStringSerializer::class) val maxPct: BigDecimal,
    val specialRule: String? = null,
)

@Serializable
data class GoldPremium(
    val institutionId: String,
    @Serializable(with = BigDecimalAsStringSerializer::class) val premiumCnyPerGram: BigDecimal,
)

@Serializable
data class GoldSettings(
    val defaultSource: String = "XAU",
    val premiums: List<GoldPremium> = emptyList(),
)

@Serializable
data class BackupSettings(
    val retainCount: Int = 5,
)

@Serializable
data class QuoteSettings(
    val timeoutMs: Long = 8_000,
    val retryCount: Int = 1,
    val maxConcurrency: Int = 6,
)

@Serializable
data class ChartSettings(
    val defaultRange: String = "YTD",
)

@Serializable
data class Settings(
    val baseCurrency: String = "CNY",
    val categories: List<Category> = emptyList(),
    val gold: GoldSettings = GoldSettings(),
    val backup: BackupSettings = BackupSettings(),
    val quote: QuoteSettings = QuoteSettings(),
    val charts: ChartSettings = ChartSettings(),
)

@Serializable
enum class InstitutionType {
    @SerialName("bank") BANK,
    @SerialName("broker") BROKER,
    @SerialName("other") OTHER,
}

@Serializable
data class Institution(
    val id: String,
    val name: String,
    val type: InstitutionType = InstitutionType.OTHER,
    val chartAlias: String? = null,
    val notes: String = "",
)

@Serializable
enum class InstrumentKind {
    @SerialName("cash") CASH,
    @SerialName("quoted") QUOTED,
}

@Serializable
enum class QuoteMode {
    @SerialName("auto") AUTO,
    @SerialName("manual") MANUAL,
    @SerialName("none") NONE,
}

@Serializable
data class QuoteConfig(
    val mode: QuoteMode = QuoteMode.AUTO,
    val source: String = "auto",
    @Serializable(with = BigDecimalAsStringSerializer::class) val manualPrice: BigDecimal? = null,
)

@Serializable
data class Precision(
    val priceDecimals: Int = 2,
    val quantityDecimals: Int = 2,
)

@Serializable
data class Instrument(
    val id: String,
    val categoryId: String,
    val code: String,
    val name: String,
    val currency: String,
    val unit: String,
    val kind: InstrumentKind,
    val quote: QuoteConfig = QuoteConfig(),
    val precision: Precision = Precision(),
    val currencyExposure: String = "auto",
    val notes: String = "",
)

@Serializable
data class PositionRef(
    val institutionId: String,
    val instrumentId: String,
)

@Serializable
data class PortfolioData(
    val schemaVersion: Int = 1,
    val meta: Meta = Meta(),
    val settings: Settings = Settings(),
    val institutions: List<Institution> = emptyList(),
    val instruments: List<Instrument> = emptyList(),
    val events: List<Event> = emptyList(),
)

fun PortfolioData.institution(id: String): Institution? =
    institutions.firstOrNull { it.id == id }

fun PortfolioData.instrument(id: String): Instrument? =
    instruments.firstOrNull { it.id == id }

fun PortfolioData.category(id: String): Category? =
    settings.categories.firstOrNull { it.id == id }

fun PortfolioData.categoryOf(instrumentId: String): Category? =
    instrument(instrumentId)?.let { category(it.categoryId) }

fun PortfolioData.goldPremium(institutionId: String): BigDecimal =
    settings.gold.premiums
        .firstOrNull { it.institutionId == institutionId }
        ?.premiumCnyPerGram
        ?: BigDecimal.ZERO
