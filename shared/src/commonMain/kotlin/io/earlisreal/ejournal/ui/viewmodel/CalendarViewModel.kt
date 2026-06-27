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
import kotlinx.coroutines.CoroutineDispatcher
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
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

/**
 * Loads the full (segment-filtered) closed-position history once, then displays one month at a time.
 * Month navigation just re-slices the loaded data — no reload — since daily summaries cover all dates.
 */
class CalendarViewModel(
    private val closedPositions: ClosedPositionService,
    initialYear: Int,
    initialMonth: Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(
        CalendarState(year = initialYear, month = initialMonth, grid = monthGrid(initialYear, initialMonth))
    )
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private var loadJob: Job? = null
    /**
     * The dataset currently displayed. Re-entering the calendar (e.g. coming back from Analysis)
     * re-runs [load] with the same key — we then keep the browsed month instead of snapping to the
     * latest. A genuine change (portfolio/segment switch, first load) snaps to the latest month.
     */
    private var loadedKey: Pair<Long?, Segment>? = null

    fun load(portfolioId: Long?, segment: Segment) {
        loadJob?.cancel()
        if (portfolioId == null) {
            loadedKey = null
            _state.value = _state.value.copy(
                summaries = emptyMap(), positionsByDay = emptyMap(), monthTotal = 0.0, loading = false,
                canGoPrevious = false, canGoNext = false,
            )
            return
        }
        val key = portfolioId to segment
        val snapToLatest = key != loadedKey
        loadedKey = key
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(dispatcher) {
            val positions = filterPositions(closedPositions.forPortfolio(portfolioId), DateRange(null, null), segment)
            val summaries = dailySummaries(positions)
            val positionsByDay = positions.groupBy { it.exitDatetime.date }
            val availableYears = summaries.keys.map { it.year }.distinct().sorted()
            val current = _state.value
            val year: Int
            val month: Int
            if (snapToLatest) {
                val latestDate = summaries.keys.maxOrNull()
                year  = latestDate?.year        ?: current.year
                month = latestDate?.monthNumber ?: current.month
            } else {
                year  = current.year
                month = current.month
            }
            _state.value = current.copy(
                year           = year,
                month          = month,
                grid           = monthGrid(year, month),
                summaries      = summaries,
                positionsByDay = positionsByDay,
                monthTotal     = monthTotal(summaries, year, month),
                loading        = false,
                availableYears = availableYears,
                selectedDate   = if (snapToLatest) null else current.selectedDate,
                canGoPrevious  = canGoPreviousMonth(summaries, year, month),
                canGoNext      = canGoNextMonth(summaries, year, month),
            )
        }
    }

    fun previousMonth() { if (_state.value.canGoPrevious) shiftMonth(-1) }
    fun nextMonth() { if (_state.value.canGoNext) shiftMonth(1) }

    private fun shiftMonth(delta: Int) {
        val shifted = LocalDate(_state.value.year, _state.value.month, 1).plus(delta, DateTimeUnit.MONTH)
        applyMonth(shifted.year, shifted.monthNumber)
    }

    fun selectDay(date: LocalDate?) {
        _state.value = _state.value.copy(selectedDate = date)
    }

    fun jumpToMonth(year: Int, month: Int) = applyMonth(year, month)

    private fun applyMonth(year: Int, month: Int) {
        val s = _state.value
        _state.value = s.copy(
            year          = year,
            month         = month,
            grid          = monthGrid(year, month),
            monthTotal    = monthTotal(s.summaries, year, month),
            selectedDate  = null,
            canGoPrevious = canGoPreviousMonth(s.summaries, year, month),
            canGoNext     = canGoNextMonth(s.summaries, year, month),
        )
    }

    private fun monthTotal(summaries: Map<LocalDate, DaySummary>, year: Int, month: Int): Double =
        summaries.values.filter { it.date.year == year && it.date.monthNumber == month }.sumOf { it.netPnl }
}

/** Month index for ordering comparisons: contiguous and monotonic across year boundaries. */
internal fun monthIndex(year: Int, month: Int): Int = year * 12 + (month - 1)

/** Earliest..latest month (as [monthIndex]) that has a trade, or null when there are no trades. */
internal fun tradeMonthBounds(summaries: Map<LocalDate, DaySummary>): IntRange? {
    if (summaries.isEmpty()) return null
    val indices = summaries.keys.map { monthIndex(it.year, it.monthNumber) }
    return indices.min()..indices.max()
}

/** Whether stepping back one month stays at or after the earliest month with a trade. */
internal fun canGoPreviousMonth(summaries: Map<LocalDate, DaySummary>, year: Int, month: Int): Boolean {
    val bounds = tradeMonthBounds(summaries) ?: return false
    return monthIndex(year, month) > bounds.first
}

/** Whether stepping forward one month stays at or before the latest month with a trade. */
internal fun canGoNextMonth(summaries: Map<LocalDate, DaySummary>, year: Int, month: Int): Boolean {
    val bounds = tradeMonthBounds(summaries) ?: return false
    return monthIndex(year, month) < bounds.last
}
