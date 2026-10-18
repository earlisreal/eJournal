package io.earlisreal.ejournal.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.defaultTagColors
import io.earlisreal.ejournal.ui.shell.FilterState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReportsState(
    val stats: List<TagStat> = emptyList(),
    val buckets: List<ReportBucket> = emptyList(),
    val grouping: ReportGrouping = ReportGrouping.ENTRY_TIME,
    val selectedKey: String? = null,
    val metric: ReportMetric? = null,
    val metricDirection: SortDirection = SortDirection.DESC,
    val positionSort: SortColumn = SortColumn.EXIT,
    val positionDirection: SortDirection = SortDirection.DESC,
    val allTags: List<Tag> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    val selectedBucket: ReportBucket? get() = buckets.firstOrNull { it.key == selectedKey }
    val cohort: List<ClosedPosition> get() = sortPositions(selectedBucket?.positions.orEmpty(), positionSort, positionDirection)
    val sortedBuckets get() = sortReportBuckets(buckets, metric, metricDirection)
    val total get() = buckets.sumOf { it.positions.size }
    val verified get() = buckets.filterNot { it.unverified }.sumOf { it.positions.size }
}

class ReportsViewModel(
    private val positionTags: PositionTagService,
    private val tagRepository: TagRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReportsState())
    val state: StateFlow<ReportsState> = _state.asStateFlow()
    private var filter: FilterState? = null
    private var filtered: List<ClosedPosition> = emptyList()
    private var loadJob: Job? = null
    private var generation = 0
    val chartListState = androidx.compose.foundation.lazy.LazyListState()
    val bucketListState = androidx.compose.foundation.lazy.LazyListState()
    val positionListState = androidx.compose.foundation.lazy.LazyListState()

    fun load(next: FilterState) {
        val changed = filter != next
        filter = next
        val request = ++generation
        loadJob?.cancel()
        if (changed) {
            filtered = emptyList()
            _state.value = _state.value.copy(selectedKey = null, stats = emptyList(), buckets = emptyList(), allTags = emptyList())
        }
        val id = next.portfolio?.id
        if (id == null) {
            filtered = emptyList()
            _state.value = _state.value.copy(stats = emptyList(), buckets = emptyList(), allTags = emptyList(), loading = false, error = null)
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        loadJob = viewModelScope.launch {
            try {
                val (positions, tags) = withContext(Dispatchers.Default) {
                    positionTags.forPortfolio(id) to tagRepository.getAll(id)
                }
                if (request != generation) return@launch
                val population = filterPositions(positions, next.dateRange, next.segment)
                filtered = filterByTags(population.filter { it.isClosed }, next.selectedTagIds, next.tagMatch)
                _state.value = _state.value.copy(stats = tagStats(population), allTags = tags)
                regroup(loading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == generation) _state.value = _state.value.copy(loading = false, error = e.message ?: "Reports could not load")
            }
        }
    }

    fun refresh() { filter?.let(::load) }

    private fun regroup(loading: Boolean = _state.value.loading) {
        val current = _state.value
        val buckets = if (current.grouping == ReportGrouping.TAG) emptyList()
            else groupedReport(filtered, current.grouping, filter?.segment ?: Segment.ALL)
        _state.value = current.copy(buckets = buckets, loading = loading, selectedKey = current.selectedKey?.takeIf { key -> buckets.any { it.key == key && it.positions.isNotEmpty() } })
    }

    fun groupBy(grouping: ReportGrouping) {
        _state.value = _state.value.copy(grouping = grouping, selectedKey = null, metric = null)
        regroup()
        viewModelScope.launch { bucketListState.scrollToItem(0); chartListState.scrollToItem(0) }
    }

    fun select(key: String?) {
        _state.value = _state.value.copy(selectedKey = key?.takeIf { k -> _state.value.buckets.any { it.key == k && it.positions.isNotEmpty() } })
        viewModelScope.launch { positionListState.scrollToItem(0) }
    }

    fun sortBy(metric: ReportMetric?) {
        val direction = if (_state.value.metric == metric && _state.value.metricDirection == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC
        _state.value = _state.value.copy(metric = metric, metricDirection = direction)
    }

    fun sortPositionsBy(column: SortColumn) {
        val direction = if (_state.value.positionSort == column && _state.value.positionDirection == SortDirection.DESC) SortDirection.ASC else SortDirection.DESC
        _state.value = _state.value.copy(positionSort = column, positionDirection = direction)
    }

    fun toggleTag(position: ClosedPosition, tag: Tag) = editTags { id ->
        if (position.tags.any { it.id == tag.id }) positionTags.removeTag(id, position, tag.id)
        else positionTags.addTag(id, position, tag.id)
    }

    fun createAndAssignTag(position: ClosedPosition, name: String) = editTags { id ->
        val color = defaultTagColors[_state.value.allTags.size % defaultTagColors.size]
        val tagId = try { tagRepository.create(id, name, color) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            tagRepository.getAll(id).firstOrNull { it.name.equals(name, ignoreCase = true) }?.id ?: throw e
        }
        positionTags.addTag(id, position, tagId)
    }

    private fun editTags(edit: suspend (Long) -> Unit) {
        val context = filter ?: return
        val id = context.portfolio?.id ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.Default) { edit(id) }
                if (filter == context) refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.value = _state.value.copy(error = e.message ?: "Tag edit failed") }
        }
    }
}
