package io.earlisreal.ejournal.domain.analytics

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

data class DateRange(val from: LocalDate?, val to: LocalDate?)   // null bound = unbounded

enum class DateRangePreset { THIS_WEEK, THIS_MONTH, THIS_QUARTER, YTD, LAST_YEAR, ALL_TIME, CUSTOM }

enum class Segment { ALL, DAY, SWING }

fun resolveRange(
    preset: DateRangePreset,
    today: LocalDate,
    custom: DateRange? = null,
): DateRange = when (preset) {
    DateRangePreset.THIS_WEEK ->
        DateRange(today.minus(today.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY), today)
    DateRangePreset.THIS_MONTH ->
        DateRange(LocalDate(today.year, today.monthNumber, 1), today)
    DateRangePreset.THIS_QUARTER -> {
        val quarterStartMonth = ((today.monthNumber - 1) / 3) * 3 + 1
        DateRange(LocalDate(today.year, quarterStartMonth, 1), today)
    }
    DateRangePreset.YTD ->
        DateRange(LocalDate(today.year, 1, 1), today)
    DateRangePreset.LAST_YEAR ->
        DateRange(LocalDate(today.year - 1, 1, 1), LocalDate(today.year - 1, 12, 31))
    DateRangePreset.ALL_TIME ->
        DateRange(null, null)
    DateRangePreset.CUSTOM ->
        custom ?: DateRange(null, null)
}
