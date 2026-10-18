package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.*
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ControlShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.ReportsState
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
    var manageTags by remember { mutableStateOf(false) }
    LaunchedEffect(filter, refreshOnStartup, refreshRevision) { vm.load(filter) }
    ReportsContent(state, filter, vm, onSelectTag, onAnalyze, onManageTags = { manageTags = true })
    if (manageTags) filter.portfolio?.let { portfolio ->
        TagManagerDialog(tagRepository = tagRepository, portfolioId = portfolio.id, portfolioName = portfolio.name,
            onChanged = vm::refresh, onTagDeleted = onTagDeleted, onDismiss = { manageTags = false })
    }
}

/** The complete screen body is renderable with synthetic state for visual verification. */
@Composable
internal fun ReportsContent(
    state: ReportsState,
    filter: FilterState,
    vm: ReportsViewModel,
    onSelectTag: (Long) -> Unit = {},
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
    onManageTags: () -> Unit = {},
) {
    val colors = AppTheme.colors
    val portfolio = filter.portfolio
    val symbol = portfolio?.market?.symbol ?: "$"
    val temporal = state.grouping in setOf(ReportGrouping.ENTRY_TIME, ReportGrouping.ENTRY_WEEKDAY, ReportGrouping.DURATION)
    val timingExplanation = when (state.grouping) {
        ReportGrouping.ENTRY_WEEKDAY -> "Weekdays need a confirmed entry date. Missing dates stay in Time unverified."
        ReportGrouping.ENTRY_TIME -> "Entry slots need a confirmed execution time. Missing times stay in Time unverified."
        else -> "Holding time needs confirmed opening and final-exit times. A day is 24 elapsed hours. Missing or conflicting times remain unverified."
    }
    var timingDetails by rememberSaveable { mutableStateOf(false) }
    val bucket = state.selectedBucket
    val metrics = remember(state.buckets, state.selectedKey) {
        bucket?.metrics ?: computeMetrics(state.buckets.flatMap { it.positions })
    }

    ScreenScaffold(title = "Reports", actions = {
        portfolio?.let { Text(it.name, style = MaterialTheme.typography.bodyMedium, color = colors.textMuted) }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 620.dp
            val pageScroll = vm.pageScrollState
            Column(Modifier.fillMaxSize().then(if (compact) Modifier.verticalScroll(pageScroll) else Modifier), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
                val tableModifier = if (compact) Modifier.fillMaxWidth().height(290.dp) else Modifier.fillMaxWidth().weight(1f)
                GroupingTabs(state.grouping, vm::groupBy)
                if (portfolio == null) {
                    EmptyState("Choose a Portfolio", "Select a Portfolio in the top bar to review its performance.")
                    return@Column
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Final exit date: ${reportRangeLabel(filter)}", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    Text(when (filter.segment) { Segment.ALL -> "Day + Swing"; Segment.DAY -> "Day Positions"; Segment.SWING -> "Swing Positions" }, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                    val tagNames = state.allTags.filter { it.id in filter.selectedTagIds }.map { it.name }
                    Text(if (filter.selectedTagIds.isEmpty()) "All Tags" else "${if (filter.tagMatch == TagMatch.ALL) "All of" else "Any of"}: ${tagNames.joinToString()}",
                        Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                }
                state.error?.let {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(it, Modifier.weight(1f), color = colors.loss, style = MaterialTheme.typography.bodyMedium)
                        AppTextButton("Retry", vm::refresh)
                    }
                }
                when {
                    state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    state.grouping == ReportGrouping.TAG -> {
                        Text("Compare all Tags", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("This comparison includes every Tag, regardless of the Tag filter. A Position with several Tags appears in each. Select a Tag to open Trade Logs.",
                            color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                        if (state.stats.isEmpty()) EmptyState("No closed Positions in this range", "Adjust the final exit date or segment in the top bar.")
                        else TagStatsTable(state.stats, symbol, onSelectTag, tableModifier)
                    }
                    state.total == 0 && !temporal -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        EmptyState("No Positions match these filters", "Try a wider final exit date range or remove a Tag filter.")
                    }
                    else -> {
                        if (state.total == 0) Text("No Positions match these filters. Try a wider final exit date range or remove a Tag filter.", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                        ReportSummary(metrics, symbol)
                        if (bucket != null) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { vm.select(null) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back to all groups", tint = colors.accent) }
                                Column(Modifier.weight(1f)) {
                                    Text(bucket.label, style = MaterialTheme.typography.titleLarge)
                                    Text("${state.grouping.label} / ${bucket.positions.size} Positions. Select a Position to open Analysis.", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                                }
                                AppTextButton("All groups", { vm.select(null) })
                                AppTextButton("Manage Tags", onManageTags)
                            }
                            if (temporal) {
                                Text("${marketClock(portfolio.market).id}   ${if (bucket.unverified) 0 else bucket.positions.size} of ${bucket.positions.size} Positions in this group have verified timing.", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                                Text(timingExplanation, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                            }
                            TradeLogsTable(positions = state.cohort, allTags = state.allTags,
                                sortColumn = state.positionSort, sortDirection = state.positionDirection,
                                onSort = vm::sortPositionsBy, symbol = symbol, onAnalyze = onAnalyze,
                                onToggleTag = vm::toggleTag, onCreateTag = vm::createAndAssignTag,
                                onManageTags = onManageTags, listState = vm.positionListState,
                                modifier = tableModifier)
                        } else {
                            AppCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Performance by ${state.grouping.label.lowercase()}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        val context = if (temporal) {
                                            "${if (state.grouping == ReportGrouping.ENTRY_TIME) "30-minute slots in " else "Market clock: "}${marketClock(portfolio.market).id}"
                                        } else "Compare results across ${if (state.grouping == ReportGrouping.SYMBOL) "symbols" else "Long and Short Positions"}"
                                        Text(context, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (temporal) TextButton(onClick = { timingDetails = !timingDetails }) {
                                        Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = colors.textMuted)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Timing: ${state.verified}/${state.total} verified", color = if (state.total > 0 && state.verified == 0) colors.accent else colors.textMuted, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                                if (temporal && (timingDetails || (state.total > 0 && state.verified == 0))) {
                                    val explanation = if (state.verified == 0 && !timingDetails) "Timing is unverified for this history. Its results are included in Time unverified."
                                    else "$timingExplanation Reimport supported broker history to enrich missing timing. Unverified Positions remain in totals."
                                    Text(explanation, color = colors.textMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
                                }
                                Spacer(Modifier.height(12.dp))
                                GroupedReportCharts(state.buckets, symbol, vm::select)
                            }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.width(12.dp))
                                Text("${state.buckets.count { it.positions.isNotEmpty() }} active groups", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                                Spacer(Modifier.weight(1f))
                                Text("Select a populated row to review its Positions", style = MaterialTheme.typography.bodySmall, color = colors.textMuted)
                            }
                            GroupedReportTable(state.sortedBuckets, symbol, state.metric, state.metricDirection, vm::sortBy, vm::select, vm.bucketListState, tableModifier, horizontal = vm.tableScrollState)
                        }
                    }
                }
            }
            if (compact) ColumnVerticalScrollbar(pageScroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun GroupingTabs(selected: ReportGrouping, onSelect: (ReportGrouping) -> Unit) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().background(colors.surface, ControlShape).border(1.dp, colors.border, ControlShape).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ReportGrouping.entries.forEach { grouping ->
            val active = selected == grouping
            Box(Modifier.weight(1f).height(36.dp).background(if (active) colors.accent.copy(alpha = .13f) else colors.surface, ControlShape)
                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(grouping) }), contentAlignment = Alignment.Center) {
                Text(grouping.label, color = if (active) colors.accent else colors.textMuted, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReportSummary(metrics: DashboardMetrics, symbol: String) {
    val colors = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.5f)) {
            Text("Net P&L", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
            Text(signedMoney(metrics.netPnl, symbol), color = when { metrics.netPnl > 0 -> colors.profit; metrics.netPnl < 0 -> colors.loss; else -> colors.textPrimary }, style = NumberTextStyle.copy(fontSize = 27.sp, letterSpacing = (-.7).sp), maxLines = 1)
        }
        listOf("Positions" to metrics.tradeCount.toString(), "Win rate" to (metrics.winRate?.let { "%.1f%%".format(it * 100) } ?: "—"),
            "Profit factor" to (metrics.profitFactor?.let { if (it.isInfinite()) "∞" else "%.2f".format(it) } ?: "—")).forEach { (label, value) ->
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(1.dp).height(32.dp).background(colors.border))
                Column(Modifier.padding(start = 20.dp)) {
                    Text(label, color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
                    Text(value, color = colors.textPrimary, style = NumberTextStyle.copy(fontSize = 21.sp))
                }
            }
        }
    }
}

private fun reportRangeLabel(filter: FilterState): String = when {
    filter.dateRange.from == null && filter.dateRange.to == null -> "All dates"
    filter.dateRange.from == null -> "Through ${filter.dateRange.to}"
    filter.dateRange.to == null -> "From ${filter.dateRange.from}"
    else -> "${filter.dateRange.from} – ${filter.dateRange.to}"
}
