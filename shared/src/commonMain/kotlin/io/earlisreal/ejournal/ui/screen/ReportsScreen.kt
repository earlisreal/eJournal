package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.*
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(
    positionTags: PositionTagService,
    tagRepository: TagRepository,
    filter: FilterState,
    refreshOnStartup: Boolean = false,
    refreshRevision: Int = 0,
    onSelectTag: (Long) -> Unit = {},
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
    onTagDeleted: (Long) -> Unit = {},
) {
    val vm = viewModel { ReportsViewModel(positionTags, tagRepository) }
    val state by vm.state.collectAsState()
    var groupingOpen by remember { mutableStateOf(false) }
    var manageTags by remember { mutableStateOf(false) }
    LaunchedEffect(filter, refreshOnStartup, refreshRevision) { vm.load(filter) }

    ScreenScaffold(title = "Reports") {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box {
                AppSecondaryButton(text = "Group: ${state.grouping.label}", onClick = { groupingOpen = true })
                DropdownMenu(expanded = groupingOpen, onDismissRequest = { groupingOpen = false }) {
                    ReportGrouping.entries.forEach { group ->
                        DropdownMenuItem(text = { Text(group.label) }, onClick = { vm.groupBy(group); groupingOpen = false })
                    }
                }
            }
            val portfolio = filter.portfolio
            if (portfolio == null) {
                EmptyState(title = "No portfolio selected", subtitle = "Import transactions to get started.")
            } else {
                val symbol = portfolio.market.symbol
                Text("${portfolio.name} · Final exit date (saved ledger): ${filter.dateRange.from ?: "start"} – ${filter.dateRange.to ?: "present"} · ${filter.segment}", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                Text("Tags: ${if (filter.selectedTagIds.isEmpty()) "All" else state.allTags.filter { it.id in filter.selectedTagIds }.joinToString { it.name }} · ${filter.tagMatch}", style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.textMuted)
                state.error?.let { Text(it, color = AppTheme.colors.loss); AppTextButton("Retry", onClick = vm::refresh) }
                when {
                    state.loading -> LoadingIndicator()
                    state.grouping == ReportGrouping.TAG -> {
                        Text("All-Tag comparison ignores the selected Tag filter. Positions with several Tags count in each. Select a Tag to open Trade Logs.", style = MaterialTheme.typography.bodySmall)
                        TagStatsTable(state.stats, symbol, onSelectTag, Modifier.fillMaxWidth().weight(1f))
                    }
                    else -> {
                        if (state.grouping in setOf(ReportGrouping.ENTRY_TIME, ReportGrouping.ENTRY_WEEKDAY, ReportGrouping.DURATION)) {
                            val evidence = when (state.grouping) {
                                ReportGrouping.ENTRY_WEEKDAY -> "Missing source-backed entry market dates remain Time unverified."
                                ReportGrouping.ENTRY_TIME -> "Missing exact opening execution Instants remain Time unverified."
                                else -> "Missing or contradictory opening/final-closing Instants remain Time unverified; elapsed days are 24 hours."
                            }
                            Text("${marketClock(portfolio.market).id} · ${state.verified}/${state.total} verified · $evidence Totals include unverified Positions.", style = MaterialTheme.typography.bodySmall)
                        }
                        val bucket = state.selectedBucket
                        if (bucket != null) {
                            Row(Modifier.fillMaxWidth()) {
                                Text("${bucket.label} · ${bucket.positions.size} Positions", Modifier.weight(1f))
                                AppTextButton("All groups", onClick = { vm.select(null) })
                                AppTextButton("Manage tags", onClick = { manageTags = true })
                            }
                            TradeLogsTable(
                                positions = state.cohort, allTags = state.allTags,
                                sortColumn = state.positionSort, sortDirection = state.positionDirection,
                                onSort = vm::sortPositionsBy, symbol = symbol, onAnalyze = onAnalyze,
                                onToggleTag = vm::toggleTag, onCreateTag = vm::createAndAssignTag,
                                onManageTags = { manageTags = true }, listState = vm.positionListState,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        } else {
                            if (state.total == 0) Text("No closed Positions match these filters.", color = AppTheme.colors.textMuted)
                            GroupedReportCharts(state.buckets, symbol, vm::select, vm.chartListState)
                            GroupedReportTable(state.sortedBuckets, symbol, state.metric, state.metricDirection, vm::sortBy, vm::select, vm.bucketListState, Modifier.fillMaxWidth().weight(1f))
                        }
                    }
                }
            }
        }
    }
    if (manageTags) filter.portfolio?.let { portfolio ->
        TagManagerDialog(tagRepository = tagRepository, portfolioId = portfolio.id, portfolioName = portfolio.name,
            onChanged = vm::refresh, onTagDeleted = onTagDeleted, onDismiss = { manageTags = false })
    }
}
