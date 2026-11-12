package com.jimbozhang.folioprism.domain.calc

import com.jimbozhang.folioprism.domain.model.Event
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val FALLBACK_OFFSET: ZoneOffset = ZoneOffset.ofHours(8)
private val SPACE_DATE_FORMATS = listOf(
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
)

fun parseEventTime(text: String): OffsetDateTime =
    runCatching { OffsetDateTime.parse(text) }.getOrElse {
        runCatching { LocalDateTime.parse(text).atOffset(FALLBACK_OFFSET) }.getOrElse {
            SPACE_DATE_FORMATS.firstNotNullOfOrNull { format ->
                runCatching { LocalDateTime.parse(text, format) }.getOrNull()
            }?.atOffset(FALLBACK_OFFSET)
                ?: LocalDate.parse(text).atStartOfDay().atOffset(FALLBACK_OFFSET)
        }
    }

fun Event.sortInstant(): OffsetDateTime = parseEventTime(date)

fun Event.localDate(): LocalDate = sortInstant().toLocalDate()

fun eventComparator(): Comparator<Event> =
    compareBy<Event>({ it.sortInstant() }, { it.createdAt }, { it.id })
