package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagStat
import io.earlisreal.ejournal.domain.analytics.filterPositions
import io.earlisreal.ejournal.domain.analytics.tagStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsState(
    val stats: List<TagStat> = emptyList(),
    val loading: Boolean = false,
)

/**
 * Per-tag performance for the Reports screen. Respects the portfolio, date range, and segment, but
 * deliberately ignores the global *tag* filter — the whole point of this screen is to compare all
 * tags side by side.
 */
class ReportsViewModel(
    private val positionTags: PositionTagService,
) : ViewModel() {

    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()

    private var loadJob: Job? = null

    fun load(portfolioId: Long?, range: DateRange, segment: Segment) {
        loadJob?.cancel()
        if (portfolioId == null) {
            _state.value = ReportsState()
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val positions = positionTags.forPortfolio(portfolioId)
            val filtered = filterPositions(positions, range, segment)
            _state.value = ReportsState(stats = tagStats(filtered), loading = false)
        }
    }
}
