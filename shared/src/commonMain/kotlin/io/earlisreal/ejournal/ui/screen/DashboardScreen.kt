package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.StatCard
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.DashboardViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    transactionRepository: TransactionRepository,
    filter: FilterState,
) {
    val vm = viewModel { DashboardViewModel(transactionRepository) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter) {
        vm.load(filter.portfolio?.id, filter.dateRange, filter.segment)
    }

    ScreenScaffold(title = "Dashboard") {
        when {
            filter.portfolio == null -> EmptyState(
                title = "No portfolio selected",
                subtitle = "Import transactions to get started.",
            )
            state.loading -> LoadingIndicator()
            else -> {
                val m = state.metrics
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                    SectionLabel("Profit & Loss")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Tile("Net P&L", money(m.netPnl), emphasized = true)
                        Tile("Gross profit", money(m.grossProfit))
                        Tile("Gross loss", money(m.grossLoss))
                        Tile("Largest win", moneyOrDash(m.largestWin))
                        Tile("Largest loss", moneyOrDash(m.largestLoss))
                        Tile("Avg win", moneyOrDash(m.avgWin))
                        Tile("Avg loss", moneyOrDash(m.avgLoss))
                    }
                    SectionLabel("Performance")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        Tile("Win rate", percentOrDash(m.winRate))
                        Tile("Profit factor", ratioOrDash(m.profitFactor))
                        Tile("Expectancy", moneyOrDash(m.expectancy))
                        Tile("Trades", m.tradeCount.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Tile(label: String, value: String, emphasized: Boolean = false) {
    StatCard(label = label, value = value, emphasized = emphasized, modifier = Modifier.width(150.dp))
}

private fun money(v: Double): String = (if (v < 0) "−$" else "$") + "%,.2f".format(kotlin.math.abs(v))
private fun moneyOrDash(v: Double?): String = if (v == null) "—" else money(v)
private fun percentOrDash(v: Double?): String = if (v == null) "—" else "%.1f%%".format(v * 100)
private fun ratioOrDash(v: Double?): String = when {
    v == null -> "—"
    v.isInfinite() -> "∞"
    else -> "%.2f".format(v)
}
