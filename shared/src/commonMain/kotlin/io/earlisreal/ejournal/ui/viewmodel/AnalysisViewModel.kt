package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.marketdata.BarAggregator
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class AnalysisViewModel(
    private val marketDataRepo: MarketDataRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AnalysisState())
    val state: StateFlow<AnalysisState> = _state.asStateFlow()

    private var positions: List<ClosedPosition> = emptyList()
    private var loadJob: Job? = null

    fun init(positions: List<ClosedPosition>, index: Int, isDarkTheme: Boolean) {
        this.positions = positions
        val position = positions.getOrNull(index) ?: return
        val defaultTf = defaultTimeframe(position)
        _state.value = AnalysisState(
            position        = position,
            currentIndex    = index,
            totalCount      = positions.size,
            activeTimeframe = defaultTf,
            vwapEnabled     = true,
            isDarkTheme     = isDarkTheme,
            loading         = true,
        )
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = _state.value.copy(has1MinData = check1MinAvailability(position))
        }
        loadBars(position, defaultTf)
    }

    fun updateTheme(isDark: Boolean) {
        _state.value = _state.value.copy(isDarkTheme = isDark)
    }

    fun selectTimeframe(tf: ChartTimeframe) {
        val position = _state.value.position ?: return
        _state.value = _state.value.copy(
            activeTimeframe = tf, loading = true, chartData = null, noDataForTimeframe = false,
        )
        loadBars(position, tf)
    }

    fun toggleVwap() {
        _state.value = _state.value.copy(vwapEnabled = !_state.value.vwapEnabled)
    }

    fun navigatePrev() = navigateTo(_state.value.currentIndex - 1)
    fun navigateNext() = navigateTo(_state.value.currentIndex + 1)

    fun navigateTo(index: Int) {
        if (index < 0 || index >= positions.size) return
        val position = positions[index]
        val tf = defaultTimeframe(position)
        _state.value = _state.value.copy(
            position = position, currentIndex = index,
            activeTimeframe = tf, loading = true, chartData = null, noDataForTimeframe = false,
        )
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = _state.value.copy(has1MinData = check1MinAvailability(position))
        }
        loadBars(position, tf)
    }

    private fun loadBars(position: ClosedPosition, tf: ChartTimeframe) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            _state.value = _state.value.copy(loading = true)
            try {
                val sourceTimeframe = when (tf) {
                    ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN -> Timeframe.ONE_MINUTE
                    ChartTimeframe.DAILY, ChartTimeframe.WEEKLY -> Timeframe.DAILY
                }
                val (from, to) = queryWindow(position, sourceTimeframe)
                val rawBars = marketDataRepo.getBars(position.symbol, sourceTimeframe, from, to)
                if (rawBars.isEmpty()) {
                    _state.value = _state.value.copy(loading = false, noDataForTimeframe = true)
                    return@launch
                }
                val aggregated = BarAggregator.aggregate(rawBars, tf)
                _state.value = _state.value.copy(loading = false, chartData = aggregated, noDataForTimeframe = false)
            } catch (e: Exception) {
                System.err.println("[analysis] loadBars error: $e")
                _state.value = _state.value.copy(loading = false, noDataForTimeframe = true)
            }
        }
    }

    private fun queryWindow(position: ClosedPosition, tf: Timeframe): Pair<LocalDateTime, LocalDateTime> {
        return if (tf == Timeframe.ONE_MINUTE) {
            LocalDateTime(position.entryDatetime.date.minus(DatePeriod(days = 1)), LocalTime(0, 0)) to
            LocalDateTime(position.exitDatetime.date.plus(DatePeriod(days = 1)),   LocalTime(23, 59))
        } else {
            LocalDateTime(position.entryDatetime.date.minus(DatePeriod(days = 90)), LocalTime(0, 0)) to
            LocalDateTime(position.exitDatetime.date.plus(DatePeriod(days = 60)),   LocalTime(23, 59))
        }
    }

    private fun defaultTimeframe(position: ClosedPosition): ChartTimeframe =
        if (classifyTradeType(position) == TradeType.DAY) ChartTimeframe.ONE_MIN else ChartTimeframe.DAILY

    private suspend fun check1MinAvailability(position: ClosedPosition): Boolean {
        val coverage = marketDataRepo.getCoverage(position.symbol, Timeframe.ONE_MINUTE) ?: return false
        val tradeDate = position.entryDatetime.date
        return tradeDate >= coverage.first.date && tradeDate <= coverage.last.date
    }
}
