package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.analytics.DashboardMetrics
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.computeMetrics
import io.earlisreal.ejournal.domain.analytics.filterPositions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val metrics: DashboardMetrics = computeMetrics(emptyList()),
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
            _state.value = DashboardState(metrics = computeMetrics(emptyList()), loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val txs = transactionRepository.getByPortfolio(portfolioId)
            val positions = FifoMatcher.computeClosedPositions(txs)
            val filtered = filterPositions(positions, range, segment)
            _state.value = DashboardState(metrics = computeMetrics(filtered), loading = false)
        }
    }
}
