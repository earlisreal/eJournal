package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.TagStatsTable
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.ReportsViewModel

@Composable
fun ReportsScreen(
    positionTags: PositionTagService,
    filter: FilterState,
    onSelectTag: (Long) -> Unit = {},
) {
    val vm = viewModel { ReportsViewModel(positionTags) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter) {
        vm.load(filter.portfolio?.id, filter.dateRange, filter.segment)
    }

    ScreenScaffold(title = "Reports") {
        when {
            filter.portfolio == null -> EmptyState(
                title = "No portfolio selected",
                subtitle = "Import transactions to get started.",
            )
            state.loading -> LoadingIndicator()
            state.stats.isEmpty() -> EmptyState(
                title = "No closed positions in this range",
                subtitle = "Adjust the date range or segment in the top bar.",
            )
            else -> {
                val symbol = filter.portfolio?.market?.symbol ?: "$"
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        "Performance by tag",
                        color = AppTheme.colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A trade with several tags counts toward each, so per-tag P&L won't sum to your total. Click a tag to see its trades.",
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    TagStatsTable(
                        stats = state.stats,
                        symbol = symbol,
                        onSelectTag = onSelectTag,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}
