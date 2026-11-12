package com.jimbozhang.folioprism.domain

import com.jimbozhang.folioprism.data.json.DefaultData
import com.jimbozhang.folioprism.domain.calc.EventValues
import com.jimbozhang.folioprism.domain.model.CategoryIds
import com.jimbozhang.folioprism.domain.model.Event
import com.jimbozhang.folioprism.domain.model.Instrument
import com.jimbozhang.folioprism.domain.model.InstrumentKind
import com.jimbozhang.folioprism.domain.model.Institution
import com.jimbozhang.folioprism.domain.model.Meta
import com.jimbozhang.folioprism.domain.model.OpeningEvent
import com.jimbozhang.folioprism.domain.model.PortfolioData
import com.jimbozhang.folioprism.domain.model.PositionRef
import com.jimbozhang.folioprism.domain.model.QuoteConfig
import com.jimbozhang.folioprism.domain.model.QuoteMode
import com.jimbozhang.folioprism.domain.model.TransferEvent
import com.jimbozhang.folioprism.domain.money.bd
import java.math.BigDecimal

const val INST_A = "inst_a"
const val INST_B = "inst_b"
const val CASH_CNY = "ins_cash_cny"
const val CASH_USD = "ins_cash_usd"
const val CASH_CNH = "ins_cash_cnh"
const val FUND = "ins_fund"
const val GROWTH = "ins_growth"
const val US_EQ = "ins_us_eq"
const val GOLD = "ins_gold"
const val DATE1 = "2026-01-01T00:00:00+08:00"
const val DATE2 = "2026-02-01T00:00:00+08:00"

fun cashInstrument(
    id: String,
    currency: String,
    categoryId: String = CategoryIds.CASH,
): Instrument = Instrument(
    id = id,
    categoryId = categoryId,
    code = currency,
    name = "$currency cash",
    currency = currency,
    unit = "元",
    kind = InstrumentKind.CASH,
    quote = QuoteConfig(mode = QuoteMode.NONE, source = "none"),
)

fun quotedInstrument(
    id: String,
    categoryId: String,
    currency: String = "CNY",
    code: String = id,
): Instrument = Instrument(
    id = id,
    categoryId = categoryId,
    code = code,
    name = id,
    currency = currency,
    unit = "份",
    kind = InstrumentKind.QUOTED,
)

fun ref(institutionId: String, instrumentId: String): PositionRef =
    PositionRef(institutionId, instrumentId)

fun portfolio(
    institutions: List<Institution> = listOf(
        Institution(INST_A, "A"),
        Institution(INST_B, "B"),
    ),
    instruments: List<Instrument> = emptyList(),
    events: List<Event> = emptyList(),
): PortfolioData = PortfolioData(
    meta = Meta(createdAt = DATE1, updatedAt = DATE1),
    settings = DefaultData.empty(DATE1).settings,
    institutions = institutions,
    instruments = instruments,
    events = events,
)

fun opening(
    position: PositionRef,
    quantity: String? = null,
    amount: String? = null,
    date: String = DATE1,
): OpeningEvent = OpeningEvent(
    position = position,
    date = date,
    quantity = quantity?.let(::bd),
    amount = amount?.let(::bd),
)

fun transfer(
    from: PositionRef,
    to: PositionRef,
    fromQuantity: String,
    toQuantity: String,
    fromPrice: String? = null,
    toPrice: String? = null,
    fromFx: String = "1",
    toFx: String = "1",
    date: String = DATE1,
): TransferEvent {
    val values = EventValues.transferValues(
        fromQuantity = bd(fromQuantity),
        toQuantity = bd(toQuantity),
        fromPrice = fromPrice?.let(::bd),
        toPrice = toPrice?.let(::bd),
        fromFxToCny = bd(fromFx),
        toFxToCny = bd(toFx),
    )
    return TransferEvent(
        from = from,
        to = to,
        fromQuantity = bd(fromQuantity),
        toQuantity = bd(toQuantity),
        fromPrice = fromPrice?.let(::bd),
        toPrice = toPrice?.let(::bd),
        fromFxToCny = bd(fromFx),
        toFxToCny = bd(toFx),
        fromValueCny = values.fromValueCny,
        toValueCny = values.toValueCny,
        lossValueCny = values.lossValueCny,
        date = date,
    )
}

fun assertDecimal(expected: String, actual: BigDecimal?) {
    val value = actual ?: throw AssertionError("Expected $expected but was null")
    if (bd(expected).compareTo(value) != 0) {
        throw AssertionError("Expected $expected but was ${value.toPlainString()}")
    }
}
