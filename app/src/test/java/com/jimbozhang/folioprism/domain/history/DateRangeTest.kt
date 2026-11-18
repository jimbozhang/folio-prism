package com.jimbozhang.folioprism.domain.history

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DateRangeTest {

    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun presetRangesEndToday() {
        assertEquals(
            DateRange(LocalDate.of(2026, 1, 1), today),
            DateRange.preset(RangePreset.YEAR_TO_DATE, today, earliest = LocalDate.of(2025, 1, 1)),
        )
        assertEquals(
            DateRange(LocalDate.of(2026, 8, 12), today),
            DateRange.preset(RangePreset.THREE_MONTHS, today, earliest = null),
        )
        assertEquals(
            DateRange(LocalDate.of(2025, 1, 1), today),
            DateRange.preset(RangePreset.ALL, today, earliest = LocalDate.of(2025, 1, 1)),
        )
    }

    @Test
    fun monthToDateStartsOnFirstDayOfMonth() {
        assertEquals(
            DateRange(LocalDate.of(2026, 11, 1), today),
            DateRange.preset(RangePreset.MONTH_TO_DATE, today, earliest = null),
        )
    }

    @Test
    fun firstDayOfMonthProducesSingleDayRange() {
        val first = LocalDate.of(2026, 11, 1)
        val range = DateRange.preset(RangePreset.MONTH_TO_DATE, first, earliest = null)
        assertEquals(first, range.from)
        assertEquals(first, range.to)
        assertEquals(1, range.days)
    }

    @Test
    fun firstDayOfYearProducesSingleDayRange() {
        val first = LocalDate.of(2026, 1, 1)
        val range = DateRange.preset(RangePreset.YEAR_TO_DATE, first, earliest = null)
        assertEquals(first, range.from)
        assertEquals(first, range.to)
        assertEquals(1, range.days)
    }

    @Test
    fun shiftBackStepsToPreviousWholeMonth() {
        val range = DateRange(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 6, 18))
        val shifted = range.shiftBack()
        assertEquals(LocalDate.of(2026, 1, 1), shifted.from)
        assertEquals(LocalDate.of(2026, 1, 31), shifted.to)

        val currentMonth = DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18))
        val back = currentMonth.shiftBack()
        assertEquals(LocalDate.of(2026, 10, 1), back.from)
        assertEquals(LocalDate.of(2026, 10, 31), back.to)
    }

    @Test
    fun shiftForwardStepsToNextWholeMonth() {
        val range = DateRange(LocalDate.of(2026, 2, 13), LocalDate.of(2026, 6, 18))
        val shifted = range.shiftForward(today)
        assertEquals(LocalDate.of(2026, 7, 1), shifted.from)
        assertEquals(LocalDate.of(2026, 7, 31), shifted.to)
    }

    @Test
    fun shiftForwardClampsCurrentMonthToToday() {
        val range = DateRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
        val shifted = range.shiftForward(today)
        assertEquals(LocalDate.of(2026, 11, 1), shifted.from)
        assertEquals(today, shifted.to)
    }

    @Test
    fun shiftForwardDisabledInCurrentMonth() {
        val range = DateRange(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 18))
        assertEquals(range, range.shiftForward(today))
        assertFalse(range.canShiftForward(today))
        assertTrue(range.shiftBack().canShiftForward(today))
    }

    @Test
    fun labelUsesPlainNumbers() {
        val range = DateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 11, 12))
        assertEquals("2026.1.1 ~ 2026.11.12", range.label())
    }

    @Test
    fun invalidRangeIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))
        }
    }
}
