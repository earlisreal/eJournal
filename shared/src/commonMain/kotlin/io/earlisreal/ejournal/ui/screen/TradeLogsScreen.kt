package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.domain.ClosedPositionService
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.TradeLogsTable
import io.earlisreal.ejournal.ui.components.signedMoney
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.TradeLogsViewModel

@Composable
fun TradeLogsScreen(
    closedPositions: ClosedPositionService,
    filter: FilterState,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
) {
    val vm = viewModel { TradeLogsViewModel(closedPositions) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter) {
        vm.load(filter.portfolio?.id, filter.dateRange, filter.segment)
    }

    ScreenScaffold(title = "Trade Logs") {
        when {
            filter.portfolio == null -> EmptyState(
                title = "No portfolio selected",
                subtitle = "Import transactions to get started.",
            )
            state.loading -> LoadingIndicator()
            state.positions.isEmpty() -> EmptyState(
                title = "No closed positions in this range",
                subtitle = "Adjust the date range or segment in the top bar.",
            )
            else -> {
                val symbol = filter.portfolio?.market?.symbol ?: "$"
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    TradeLogsSummary(state.positions, symbol)
                    TradeLogsTable(
                        positions = state.positions,
                        sortColumn = state.sortColumn,
                        sortDirection = state.sortDirection,
                        onSort = vm::sortBy,
                        symbol = symbol,
                        onAnalyze = onAnalyze,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            }
        }
    }
}

/** Lead-with-the-number summary for the filtered set: net P&L, then count and win rate as context. */
@Composable
private fun TradeLogsSummary(positions: List<ClosedPosition>, symbol: String) {
    val net = positions.sumOf { it.profitLoss }
    val wins = positions.count { it.profitLoss > 0.0 }
    val losses = positions.count { it.profitLoss < 0.0 }
    val decided = wins + losses
    val winRate = if (decided > 0) wins.toDouble() / decided * 100.0 else null
    val context = buildString {
        append(positions.size)
        append(if (positions.size == 1) " trade" else " trades")
        append("  ·  ${wins}W / ${losses}L")
        winRate?.let { append("  ·  %.1f%% win rate".format(it)) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("NET P&L", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
            Text(
                signedMoney(net, symbol),
                color = if (net >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss,
                style = NumberTextStyle,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(context, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}
