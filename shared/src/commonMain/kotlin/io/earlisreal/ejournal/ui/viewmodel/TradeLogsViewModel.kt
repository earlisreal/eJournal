package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.SortColumn
import io.earlisreal.ejournal.domain.analytics.SortDirection
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.analytics.filterByTags
import io.earlisreal.ejournal.domain.analytics.filterPositions
import io.earlisreal.ejournal.domain.analytics.sortPositions
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.defaultTagColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TradeLogsState(
    val positions: List<ClosedPosition> = emptyList(),
    val allTags: List<Tag> = emptyList(),
    val sortColumn: SortColumn = SortColumn.EXIT,
    val sortDirection: SortDirection = SortDirection.DESC,
    val loading: Boolean = false,
)

class TradeLogsViewModel(
    private val positionTags: PositionTagService,
    private val tagRepository: TagRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TradeLogsState())
    val state: StateFlow<TradeLogsState> = _state.asStateFlow()

    private var filtered: List<ClosedPosition> = emptyList()
    private var loadJob: Job? = null

    // Remembered so tag edits can reload the same view (positions come from cache; tags re-hydrate).
    private var lastPortfolioId: Long? = null
    private var lastRange: DateRange = DateRange(null, null)
    private var lastSegment: Segment = Segment.ALL
    private var lastSelectedTagIds: Set<Long> = emptySet()
    private var lastTagMatch: TagMatch = TagMatch.ANY

    fun load(
        portfolioId: Long?,
        range: DateRange,
        segment: Segment,
        selectedTagIds: Set<Long> = emptySet(),
        tagMatch: TagMatch = TagMatch.ANY,
    ) {
        lastPortfolioId = portfolioId
        lastRange = range
        lastSegment = segment
        lastSelectedTagIds = selectedTagIds
        lastTagMatch = tagMatch
        loadJob?.cancel()
        if (portfolioId == null) {
            filtered = emptyList()
            _state.value = _state.value.copy(positions = emptyList(), loading = false)
            return
        }
        _state.value = _state.value.copy(loading = true)
        loadJob = viewModelScope.launch(Dispatchers.Default) {
            val positions = positionTags.forPortfolio(portfolioId)
            val tags = tagRepository.getAll()
            filtered = filterByTags(filterPositions(positions, range, segment), selectedTagIds, tagMatch)
            _state.value = _state.value.copy(
                positions = sortPositions(filtered, _state.value.sortColumn, _state.value.sortDirection),
                allTags = tags,
                loading = false,
            )
        }
    }

    private fun reload() {
        load(lastPortfolioId, lastRange, lastSegment, lastSelectedTagIds, lastTagMatch)
    }

    fun sortBy(column: SortColumn) {
        val direction = if (_state.value.sortColumn == column && _state.value.sortDirection == SortDirection.DESC)
            SortDirection.ASC else SortDirection.DESC
        _state.value = _state.value.copy(
            sortColumn = column,
            sortDirection = direction,
            positions = sortPositions(filtered, column, direction),
        )
    }

    /** Adds the tag if the position doesn't have it, else removes it; then reloads to reflect the change. */
    fun toggleTag(position: ClosedPosition, tag: Tag) {
        viewModelScope.launch(Dispatchers.Default) {
            if (position.tags.any { it.id == tag.id }) positionTags.removeTag(position, tag.id)
            else positionTags.addTag(position, tag.id)
            reload()
        }
    }

    /** Quick-creates a tag (cycling the default palette) and assigns it to the position. */
    fun createAndAssignTag(position: ClosedPosition, name: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val color = defaultTagColors[_state.value.allTags.size % defaultTagColors.size]
            val id = try {
                tagRepository.create(name, color)
            } catch (e: Exception) {
                // Name already exists (case-insensitive) — assign the existing one instead.
                tagRepository.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            }
            if (id != null) positionTags.addTag(position, id)
            reload()
        }
    }

    /** Reload after the tag manager changed the vocabulary (rename/recolor/delete). */
    fun onTagsChanged() {
        reload()
    }
}
