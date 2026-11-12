package com.jimbozhang.folioprism.domain.history

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Inclusive date range used by the history charts.
 */
data class DateRange(
    val from: LocalDate,
    val to: LocalDate,
) {
    init {
        require(!from.isAfter(to)) { "from must not be after to" }
    }

    val days: Long = ChronoUnit.DAYS.between(from, to) + 1

    fun shiftBack(): DateRange =
        DateRange(from.minusDays(days), to.minusDays(days))

    /** Shifts forward without crossing [today]; returns the same range at the boundary. */
    fun shiftForward(today: LocalDate): DateRange {
        if (!to.isBefore(today)) return this
        val shift = minOf(days, ChronoUnit.DAYS.between(to, today))
        return DateRange(from.plusDays(shift), to.plusDays(shift))
    }

    fun label(): String =
        "${from.year}.${from.monthValue}.${from.dayOfMonth}" +
            " ~ ${to.year}.${to.monthValue}.${to.dayOfMonth}"

    companion object {
        fun preset(preset: RangePreset, today: LocalDate, earliest: LocalDate?): DateRange {
            val from = when (preset) {
                RangePreset.MONTH_TO_DATE -> today.withDayOfMonth(1)
                RangePreset.ONE_MONTH -> today.minusMonths(1)
                RangePreset.THREE_MONTHS -> today.minusMonths(3)
                RangePreset.YEAR_TO_DATE -> LocalDate.of(today.year, 1, 1)
                RangePreset.ONE_YEAR -> today.minusYears(1)
                RangePreset.ALL -> earliest ?: today.minusYears(1)
            }
            return DateRange(minOf(from, today), today)
        }

        fun yearToDate(today: LocalDate): DateRange =
            preset(RangePreset.YEAR_TO_DATE, today, earliest = null)
    }
}

enum class RangePreset(val label: String) {
    MONTH_TO_DATE("月初至今"),
    ONE_MONTH("近 1 月"),
    THREE_MONTHS("近 3 月"),
    YEAR_TO_DATE("今年至今"),
    ONE_YEAR("近 1 年"),
    ALL("全部"),
}
