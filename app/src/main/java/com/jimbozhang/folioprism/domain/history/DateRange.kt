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

    /**
     * Steps back to the previous whole month: the result is the full month
     * preceding [from]'s month, regardless of the current range length.
     */
    fun shiftBack(): DateRange {
        val firstOfPrevious = from.withDayOfMonth(1).minusMonths(1)
        return DateRange(
            firstOfPrevious,
            firstOfPrevious.withDayOfMonth(firstOfPrevious.lengthOfMonth()),
        )
    }

    /**
     * Steps forward to the next whole month: the result is the full month
     * following [to]'s month, clamped to [today] when that month is the
     * current one. Returns the same range at the boundary (the range already
     * ends in the current month or later).
     */
    fun shiftForward(today: LocalDate): DateRange {
        val firstOfNext = to.withDayOfMonth(1).plusMonths(1)
        if (firstOfNext.isAfter(today)) return this
        val lastOfNext = firstOfNext.withDayOfMonth(firstOfNext.lengthOfMonth())
        return DateRange(firstOfNext, minOf(lastOfNext, today))
    }

    /** Whether [shiftForward] would actually move the range. */
    fun canShiftForward(today: LocalDate): Boolean =
        to.withDayOfMonth(1).plusMonths(1) <= today

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
