package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.SortColumn
import io.earlisreal.ejournal.domain.analytics.SortDirection
import io.earlisreal.ejournal.domain.analytics.filterPositions
import io.earlisreal.ejournal.domain.analytics.sortPositions
import io.earlisreal.ejournal.domain.model.ClosedPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TradeLogsState(
    val positions: List<ClosedPosition> = emptyList(),
    val sortColumn: SortColumn = SortColumn.EXIT,
    val sortDirection: SortDirection = SortDirection.DESC,
    val loading: Boolean = false,
)

class TradeLogsViewModel(
    private val transactionRepository: TransactionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TradeLogsState())
    val state: StateFlow<TradeLogsState> = _state.asStateFlow()

    private var filtered: List<ClosedPosition> = emptyList()
    private var loadJob: Job? = null

    fun load(portfolioId: Long?, range: DateRange, segment: Segment) {
        loadJob?.cancel()
        if (portfolioId == null) {
            filtered = emptyList()
            _state.value = _state.value.copy(positions = emptyList(), loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val txs = transactionRepository.getByPortfolio(portfolioId)
            val positions = FifoMatcher.computeClosedPositions(txs)
            filtered = filterPositions(positions, range, segment)
            _state.value = _state.value.copy(
                positions = sortPositions(filtered, _state.value.sortColumn, _state.value.sortDirection),
                loading = false,
            )
        }
    }

    fun sortBy(column: SortColumn) {
        val direction = if (_state.value.sortColumn == column && _state.value.sortDirection == SortDirection.DESC)
            SortDirection.ASC else if (_state.value.sortColumn == column) SortDirection.DESC
        else SortDirection.DESC
        _state.value = _state.value.copy(
            sortColumn = column,
            sortDirection = direction,
            positions = sortPositions(filtered, column, direction),
        )
    }
}
