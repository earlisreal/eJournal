package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.analytics.DashboardMetrics
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.EquityPoint
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.computeMetrics
import io.earlisreal.ejournal.domain.analytics.equityCurve
import io.earlisreal.ejournal.domain.analytics.filterPositions
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val RECENT_TRADE_LIMIT = 8
private const val TOP_TRADE_LIMIT = 5

data class DashboardState(
    val metrics: DashboardMetrics = computeMetrics(emptyList()),
    val equityCurve: List<EquityPoint> = emptyList(),
    val recentTrades: List<ClosedPosition> = emptyList(),
    /** Biggest individual winners / losers, each ranked by P&L. */
    val topTrades: List<ClosedPosition> = emptyList(),
    val worstTrades: List<ClosedPosition> = emptyList(),
    val loading: Boolean = false,
)

class DashboardViewModel(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(portfolioId: Long?, range: DateRange, segment: Segment) {
        loadJob?.cancel()
        if (portfolioId == null) {
            _state.value = DashboardState()
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val txs = transactionRepository.getByPortfolio(portfolioId)
            val positions = FifoMatcher.computeClosedPositions(txs)
            val filtered = filterPositions(positions, range, segment)
            val byRecency = filtered.sortedByDescending { it.exitDatetime }
            _state.value = DashboardState(
                metrics = computeMetrics(filtered),
                equityCurve = equityCurve(filtered),
                recentTrades = byRecency.take(RECENT_TRADE_LIMIT),
                topTrades = filtered.filter { it.profitLoss > 0.0 }.sortedByDescending { it.profitLoss }.take(TOP_TRADE_LIMIT),
                worstTrades = filtered.filter { it.profitLoss < 0.0 }.sortedBy { it.profitLoss }.take(TOP_TRADE_LIMIT),
                loading = false,
            )
        }
    }
}
