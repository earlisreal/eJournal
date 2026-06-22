package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.domain.ClosedPositionService
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DaySummary
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.dailySummaries
import io.earlisreal.ejournal.domain.analytics.filterPositions
import io.earlisreal.ejournal.domain.analytics.monthGrid
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class CalendarState(
    val year: Int,
    val month: Int,
    val grid: List<LocalDate?> = emptyList(),
    val summaries: Map<LocalDate, DaySummary> = emptyMap(),
    val positionsByDay: Map<LocalDate, List<ClosedPosition>> = emptyMap(),
    val monthTotal: Double = 0.0,
    val selectedDate: LocalDate? = null,
    val loading: Boolean = false,
    val availableYears: List<Int> = emptyList(),
)

/**
 * Loads the full (segment-filtered) closed-position history once, then displays one month at a time.
 * Month navigation just re-slices the loaded data — no reload — since daily summaries cover all dates.
 */
class CalendarViewModel(
    private val closedPositions: ClosedPositionService,
    initialYear: Int,
    initialMonth: Int,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CalendarState(year = initialYear, month = initialMonth, grid = monthGrid(initialYear, initialMonth))
    )
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(portfolioId: Long?, segment: Segment) {
        loadJob?.cancel()
        if (portfolioId == null) {
            _state.value = _state.value.copy(
                summaries = emptyMap(), positionsByDay = emptyMap(), monthTotal = 0.0, loading = false,
            )
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val positions = filterPositions(closedPositions.forPortfolio(portfolioId), DateRange(null, null), segment)
            val summaries = dailySummaries(positions)
            val positionsByDay = positions.groupBy { it.exitDatetime.date }
            val latestDate = summaries.keys.maxOrNull()
            val displayYear  = latestDate?.year        ?: _state.value.year
            val displayMonth = latestDate?.monthNumber ?: _state.value.month
            val availableYears = summaries.keys.map { it.year }.distinct().sorted()
            _state.value = _state.value.copy(
                year           = displayYear,
                month          = displayMonth,
                grid           = monthGrid(displayYear, displayMonth),
                summaries      = summaries,
                positionsByDay = positionsByDay,
                monthTotal     = monthTotal(summaries, displayYear, displayMonth),
                loading        = false,
                availableYears = availableYears,
            )
        }
    }

    fun previousMonth() = shiftMonth(-1)
    fun nextMonth() = shiftMonth(1)

    private fun shiftMonth(delta: Int) {
        val shifted = LocalDate(_state.value.year, _state.value.month, 1).plus(delta, DateTimeUnit.MONTH)
        _state.value = _state.value.copy(
            year = shifted.year,
            month = shifted.monthNumber,
            grid = monthGrid(shifted.year, shifted.monthNumber),
            monthTotal = monthTotal(_state.value.summaries, shifted.year, shifted.monthNumber),
            selectedDate = null,
        )
    }

    fun selectDay(date: LocalDate?) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun jumpToMonth(year: Int, month: Int) {
        _state.value = _state.value.copy(
            year         = year,
            month        = month,
            grid         = monthGrid(year, month),
            monthTotal   = monthTotal(_state.value.summaries, year, month),
            selectedDate = null,
        )
    }

    private fun monthTotal(summaries: Map<LocalDate, DaySummary>, year: Int, month: Int): Double =
        summaries.values.filter { it.date.year == year && it.date.monthNumber == month }.sumOf { it.netPnl }
}
