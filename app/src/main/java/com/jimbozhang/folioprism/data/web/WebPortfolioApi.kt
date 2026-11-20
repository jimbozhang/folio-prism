package com.jimbozhang.folioprism.data.web

import com.jimbozhang.folioprism.data.json.PortfolioJson
import com.jimbozhang.folioprism.data.price.LivePriceProvider
import com.jimbozhang.folioprism.data.store.PortfolioRepository
import com.jimbozhang.folioprism.domain.calc.DividendGroup
import com.jimbozhang.folioprism.domain.calc.DividendStatsCalculator
import com.jimbozhang.folioprism.domain.calc.EventReplay
import com.jimbozhang.folioprism.domain.calc.SnapshotCalculator
import com.jimbozhang.folioprism.domain.history.DateRange
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.ExpenseEvent
import com.jimbozhang.folioprism.domain.model.IncomeEvent
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.model.institution
import com.jimbozhang.folioprism.domain.model.instrument
import com.jimbozhang.folioprism.domain.quote.QuoteSnapshot
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.serialization.decodeFromString

/**
 * Read-only computation plus ledger writes for the web UI. Computations call
 * the same domain engine as the app; writes go through the existing
 * [PortfolioRepository] so atomic writes, backups and event sorting are
 * unchanged.
 */
class WebPortfolioApi(
    private val repository: PortfolioRepository,
    private val dataFile: File,
    private val quotes: QuoteSource,
    private val history: HistorySource,
    private val today: () -> LocalDate = { LocalDate.now() },
) : WebApi {

    override suspend fun portfolio(): String? =
        if (dataFile.exists()) runCatching { dataFile.readText() }.getOrNull() else null

    override suspend fun replaceData(rawJson: String): WriteResult {
        // Strict decode with the app's data-file serializer: incompatible
        // schemas or unknown keys are rejected before touching the file.
        val data = runCatching { PortfolioJson.decode(rawJson) }
            .getOrElse { return WriteResult.Invalid("文件格式不兼容") }
        val updated = runCatching { repository.save(data) }
            .getOrElse { return WriteResult.Failed(it.message ?: "保存失败") }
        return WriteResult.Ok(updated.meta.updatedAt)
    }

    /**
     * Saves an uploaded APK into the data directory next to [dataFile]. Same
     * names overwrite in place so a phone can be updated straight from the web
     * UI. The name is taken from the request, never from the source file.
     */
    override suspend fun uploadApk(fileName: String, source: File?): ApkUploadResult {
        // Browsers send a bare filename; reject anything that still carries
        // path separators so a crafted name cannot escape the data directory.
        val clean = fileName.trim()
        if (clean.isEmpty() || clean == "." || clean == ".." ||
            clean.contains('/') || clean.contains('\\') || clean.contains('\u0000')
        ) {
            return ApkUploadResult.Invalid("文件名无效")
        }
        if (!clean.endsWith(".apk", ignoreCase = true)) {
            return ApkUploadResult.Invalid("只允许上传 .apk 文件")
        }
        if (source == null || !source.exists() || source.length() == 0L) {
            return ApkUploadResult.Invalid("未收到文件内容")
        }
        if (!isApk(source)) {
            return ApkUploadResult.Invalid("文件不是有效的 APK")
        }
        val targetDir = dataFile.parentFile ?: return ApkUploadResult.Failed("无法确定保存目录")
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return ApkUploadResult.Failed("无法创建保存目录")
        }
        return runCatching {
            val target = File(targetDir, clean)
            source.copyTo(target, overwrite = true)
            ApkUploadResult.Ok(clean, target.length())
        }.getOrElse { ApkUploadResult.Failed(it.message ?: "保存失败") }
    }

    /** APKs are ZIP archives: the first bytes must be the local file header. */
    private fun isApk(file: File): Boolean {
        val magic = ByteArray(4)
        file.inputStream().use { input ->
            var read = 0
            while (read < 4) {
                val n = input.read(magic, read, 4 - read)
                if (n < 0) break
                read += n
            }
            if (read < 4) return false
        }
        return magic.contentEquals(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
    }

    override suspend fun overview(): OverviewDto {
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        val positions = EventReplay.replay(data.events)
        val snapshot = SnapshotCalculator.build(
            data = data,
            positions = positions,
            provider = LivePriceProvider(quoteSnapshot),
            date = today(),
        )
        val total = snapshot.totalValueCny
        val categoryOrder = data.settings.categories.associate { it.id to it.order }

        val positionViews = positions.entries
            .filter { it.value.signum() != 0 }
            .mapNotNull { (key, quantity) ->
                val instrument = data.instrument(key.instrumentId) ?: return@mapNotNull null
                val value = snapshot.positionValues[key]
                val price = if (instrument.kind == InstrumentKind.CASH) {
                    BigDecimal.ONE
                } else {
                    quoteSnapshot.quotes[instrument.id]?.price ?: instrument.quote.manualPrice
                }
                PositionViewDto(
                    institutionId = key.institutionId,
                    institutionName = data.institution(key.institutionId)?.name ?: key.institutionId,
                    instrumentId = instrument.id,
                    instrumentCode = instrument.code,
                    instrumentName = instrument.name,
                    categoryId = instrument.categoryId,
                    kind = if (instrument.kind == InstrumentKind.CASH) "cash" else "quoted",
                    currency = instrument.currency,
                    unit = instrument.unit,
                    quantity = quantity.plain(),
                    price = price.plainOrNull(),
                    valueCny = value.plainOrNull(),
                    incomplete = value == null,
                ) to value
            }
            .sortedWith(
                compareBy<Pair<PositionViewDto, BigDecimal?>> {
                    categoryOrder[it.first.categoryId] ?: Int.MAX_VALUE
                }.thenByDescending { it.second ?: BigDecimal.valueOf(Long.MIN_VALUE) },
            )
            .map { it.first }

        return OverviewDto(
            version = data.meta.updatedAt,
            updatedAt = data.meta.updatedAt,
            asOf = today().toString(),
            counts = CountsDto(
                institutions = data.institutions.size,
                instruments = data.instruments.size,
                events = data.events.size,
            ),
            totalValueCny = total.plain(),
            dividendValueCny = snapshot.dividendValueCny.plain(),
            dividendShare = snapshot.dividendShare.plain(),
            portfolioValueCny = snapshot.portfolioValueCny.plain(),
            portfolioShare = snapshot.portfolioShare.plain(),
            hasMissingPrices = snapshot.hasMissingPrices,
            categories = snapshot.categories.map { category ->
                CategoryViewDto(
                    id = category.category.id,
                    name = category.category.name,
                    color = category.category.color,
                    order = category.category.order,
                    specialRule = category.category.specialRule,
                    valueCny = category.valueCny.plain(),
                    share = category.share.plain(),
                    targetPct = category.category.targetPct.plain(),
                    minPct = category.category.minPct.plain(),
                    maxPct = category.category.maxPct.plain(),
                    inRange = category.inRange,
                    incomplete = category.incomplete,
                    targetValueCny = category.targetValueCny.plainOrNull(),
                    minValueCny = category.minValueCny.plainOrNull(),
                    maxValueCny = category.maxValueCny.plainOrNull(),
                    gapToTargetCny = category.gapToTargetCny.plainOrNull(),
                    gapToMinCny = category.gapToMinCny.plainOrNull(),
                    gapToMaxCny = category.gapToMaxCny.plainOrNull(),
                    visualShare = category.visualShare.plain(),
                    visualTarget = category.visualTarget.plain(),
                    visualMin = category.visualMin.plain(),
                    visualMax = category.visualMax.plain(),
                    goldMinPriceCnyPerGram = category.goldMinPriceCnyPerGram.plainOrNull(),
                    goldMaxPriceCnyPerGram = category.goldMaxPriceCnyPerGram.plainOrNull(),
                )
            },
            positions = positionViews,
            institutions = snapshot.brokerValues.entries
                .sortedByDescending { it.value }
                .map { (id, value) ->
                    val institution = data.institution(id)
                    DistributionDto(
                        id = id,
                        name = institution?.chartAlias ?: institution?.name ?: id,
                        valueCny = value.plain(),
                        share = SnapshotCalculator.ratio(value, total).plain(),
                    )
                },
            currencies = snapshot.currencyValues.entries
                .sortedByDescending { it.value }
                .map { (code, value) ->
                    DistributionDto(
                        id = code,
                        name = currencyLabel(code),
                        valueCny = value.plain(),
                        share = SnapshotCalculator.ratio(value, total).plain(),
                    )
                },
            quote = quoteStatus(quoteSnapshot),
        )
    }

    override suspend fun chart(
        type: String,
        from: LocalDate,
        to: LocalDate,
        categoryId: String?,
    ): ChartDto {
        require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        val result = history.load(data, from, to, quoteSnapshot)
        val base = ChartDto(
            type = type,
            from = from.toString(),
            to = to.toString(),
            logs = result.logs,
        )
        return when (type) {
            "assets" -> {
                val pnls = result.categoryPnl.mapNotNull { it.pnlCny }
                base.copy(
                    points = result.curve.map {
                        ChartPointDto(it.date.toString(), it.totalCny.plainOrNull(), it.incomplete)
                    },
                    summary = ChartSummaryDto(
                        startValue = result.curve.firstOrNull()?.totalCny.plainOrNull(),
                        endValue = result.curve.lastOrNull()?.totalCny.plainOrNull(),
                        totalPnl = if (pnls.isEmpty()) null else pnls.fold(BigDecimal.ZERO, BigDecimal::add).plain(),
                    ),
                )
            }

            "category_assets" -> {
                val id = categoryId ?: throw IllegalArgumentException("请选择类别")
                val category = data.settings.categories.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException("类别不存在")
                val points = result.curve.map { day ->
                    val value = day.categoryValues[id]
                    ChartPointDto(day.date.toString(), value.plainOrNull(), value == null)
                }
                base.copy(
                    points = points,
                    category = CategoryRefDto(category.id, category.name, category.color),
                    summary = ChartSummaryDto(
                        startValue = points.firstOrNull()?.value,
                        endValue = points.lastOrNull()?.value,
                    ),
                )
            }

            "category_pnl" -> {
                val pnl = result.categoryPnl.map { item ->
                    CategoryPnlDto(
                        id = item.categoryId,
                        name = item.categoryName,
                        color = data.settings.categories.firstOrNull { it.id == item.categoryId }?.color
                            ?: "#8A8A8A",
                        pnlCny = item.pnlCny.plainOrNull(),
                        incomplete = item.incomplete,
                    )
                }
                val values = pnl.mapNotNull { it.pnlCny?.toBigDecimalOrNull() }
                base.copy(
                    categoryPnl = pnl,
                    summary = ChartSummaryDto(
                        totalPnl = if (values.isEmpty()) null else values.fold(BigDecimal.ZERO, BigDecimal::add).plain(),
                    ),
                )
            }

            else -> throw IllegalArgumentException("未知图表类型")
        }
    }

    override suspend fun flows(from: LocalDate, to: LocalDate): FlowsDto {
        require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        return FlowsDto(
            WebStats.monthlyFlows(data, quoteSnapshot.fxRates, DateRange(from, to)),
        )
    }

    override suspend fun expenses(from: LocalDate, to: LocalDate): ExpensesDto {
        require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        val categoryNames = data.settings.categories.associate { it.id to it.name }
        return ExpensesDto(
            WebStats.monthlyExpenses(data, categoryNames, quoteSnapshot.fxRates, DateRange(from, to)),
        )
    }

    override suspend fun dividends(from: LocalDate, to: LocalDate): DividendsDto {
        require(!from.isAfter(to)) { "开始日期不能晚于结束日期" }
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        val stats = DividendStatsCalculator.calculate(data, quoteSnapshot.fxRates, from, to)
        return DividendsDto(
            totalCny = stats.totalCny.plain(),
            count = stats.count,
            incomplete = stats.incomplete,
            averagePerMonthCny = stats.averagePerMonthCny.plainOrNull(),
            withdrawnCny = stats.withdrawnCny.plain(),
            withdrawnCount = stats.withdrawnCount,
            retainedCny = stats.retainedCny.plain(),
            retainedCount = stats.retainedCount,
            monthly = stats.monthly.map {
                DividendMonthDto(it.month.toString(), it.amountCny.plain(), it.count)
            },
            bySourceCategory = stats.bySourceCategory.map(::toDividendGroupDto),
            bySourceInstrument = stats.bySourceInstrument.map(::toDividendGroupDto),
            byDestinationAccount = stats.byDestinationAccount.map(::toDividendGroupDto),
            largest = stats.largest?.let { DividendRecordDto(it.date.toString(), it.amountCny.plain()) },
            latest = stats.latest?.let { DividendRecordDto(it.date.toString(), it.amountCny.plain()) },
        )
    }

    override suspend fun refreshQuotes(): QuoteStatusDto {
        val data = currentData()
        return quoteStatus(quotes.refresh(data))
    }

    override suspend fun createEvent(body: String): WriteResult = writeEvent(null, body)

    override suspend fun updateEvent(id: String, body: String): WriteResult {
        val existing = currentData().events.firstOrNull { it.id == id } ?: return WriteResult.NotFound
        return writeEvent(existing, body)
    }

    override suspend fun deleteEvent(id: String): WriteResult {
        val existing = currentData().events.firstOrNull { it.id == id } ?: return WriteResult.NotFound
        val updated = runCatching {
            repository.update { current ->
                val remove = linkedIdsToRemove(current, existing)
                current.copy(events = current.events.filterNot { it.id in remove })
            }
        }.getOrElse { return WriteResult.Failed(it.message ?: "保存失败") }
        return WriteResult.Ok(updated.meta.updatedAt)
    }

    private suspend fun writeEvent(initial: Event?, body: String): WriteResult {
        val data = currentData()
        val quoteSnapshot = quotes.ensure(data)
        val draft = runCatching { WEB_JSON.decodeFromString<WebEventDraft>(body) }
            .getOrElse { return WriteResult.Invalid("请求格式无效") }
        val newEvents = WebEventBuilder.build(data, quoteSnapshot, draft, initial)
            .getOrElse { return WriteResult.Invalid(it.message ?: "校验失败") }
        val updated = runCatching {
            repository.update { current ->
                var events = current.events
                if (initial != null) {
                    val remove = linkedIdsToRemove(current, initial)
                    events = events.filterNot { it.id in remove }
                }
                current.copy(events = events + newEvents)
            }
        }.getOrElse { return WriteResult.Failed(it.message ?: "保存失败") }
        return WriteResult.Ok(updated.meta.updatedAt)
    }

    private fun toDividendGroupDto(group: DividendGroup) = DividendGroupDto(
        id = group.id,
        name = group.name,
        amountCny = group.amountCny.plain(),
        count = group.count,
    )

    private suspend fun currentData(): PortfolioData =
        repository.data.value ?: repository.load()

    private fun quoteStatus(snapshot: QuoteSnapshot): QuoteStatusDto = QuoteStatusDto(
        fetchedAt = quotes.fetchedAt(),
        failures = snapshot.failures.map { failure ->
            QuoteFailureDto(
                instrumentId = failure.instrumentId,
                code = failure.instrumentCode,
                name = failure.instrumentName,
                kind = failure.kind.name,
                source = failure.source,
                message = failure.message,
            )
        },
    )

    /** Flags linked events that must go when [event] is replaced or deleted. */
    private fun linkedIdsToRemove(data: PortfolioData, event: Event): Set<String> {
        val ids = mutableSetOf(event.id)
        when (event) {
            is TransferEvent -> data.events
                .filterIsInstance<ExpenseEvent>()
                .filter { it.linkedTransferId == event.id }
                .forEach { ids += it.id }

            is IncomeEvent -> event.linkedEventId?.let { ids += it }
            else -> Unit
        }
        return ids
    }

    private fun currencyLabel(code: String): String = when (code) {
        "CNY" -> "人民币"
        "USD" -> "美元"
        "HKD" -> "港元"
        "PHYSICAL_GOLD" -> "实物黄金"
        else -> code
    }
}
