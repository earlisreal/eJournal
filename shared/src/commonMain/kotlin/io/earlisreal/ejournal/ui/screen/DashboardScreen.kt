package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.ColumnVerticalScrollbar
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.EquityCurveChart
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.RecentTradesList
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.StatCard
import io.earlisreal.ejournal.ui.components.TagStatsCompactList
import io.earlisreal.ejournal.ui.components.TopTradesList
import io.earlisreal.ejournal.ui.components.formatDuration
import io.earlisreal.ejournal.ui.components.signedMoney
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.HeroNumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.DashboardState
import io.earlisreal.ejournal.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    positionTags: PositionTagService,
    filter: FilterState,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
    onViewAllTrades: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onSelectTag: (Long) -> Unit = {},
) {
    val vm = viewModel { DashboardViewModel(positionTags) }
    val state by vm.state.collectAsState()

    LaunchedEffect(filter) {
        vm.load(filter.portfolio?.id, filter.dateRange, filter.segment, filter.selectedTagIds, filter.tagMatch)
    }

    ScreenScaffold(title = "Dashboard") {
        when {
            filter.portfolio == null -> EmptyState(
                title = "No portfolio selected",
                subtitle = "Import transactions to get started.",
            )
            state.loading -> LoadingIndicator()
            state.metrics.tradeCount == 0 -> EmptyState(
                title = "No closed positions in this range",
                subtitle = "Adjust the date range or segment in the top bar.",
            )
            else -> DashboardContent(
                state = state,
                symbol = filter.portfolio?.market?.symbol ?: "$",
                onAnalyze = onAnalyze,
                onViewAllTrades = onViewAllTrades,
                onOpenReports = onOpenReports,
                onSelectTag = onSelectTag,
            )
        }
    }
}

/** Stateless dashboard layout — fed a computed [DashboardState]. Previewable in isolation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DashboardContent(
    state: DashboardState,
    symbol: String,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit,
    onViewAllTrades: () -> Unit,
    onOpenReports: () -> Unit = {},
    onSelectTag: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val metrics = state.metrics
    val scrollState = rememberScrollState()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HeroBlock(state = state, symbol = symbol)

            SectionLabel("Profit & Loss")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Tile("Gross profit", money(metrics.grossProfit, symbol))
                Tile("Gross loss", money(metrics.grossLoss, symbol))
                Tile("Largest win", moneyOrDash(metrics.largestWin, symbol))
                Tile("Largest loss", moneyOrDash(metrics.largestLoss, symbol))
                Tile("Avg win", moneyOrDash(metrics.avgWin, symbol))
                Tile("Avg loss", moneyOrDash(metrics.avgLoss, symbol))
            }

            SectionLabel("Performance")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Tile("Win rate", percentOrDash(metrics.winRate))
                Tile("Winners", metrics.winCount.toString())
                Tile("Losers", metrics.lossCount.toString())
                Tile("Trades", metrics.tradeCount.toString())
                if (metrics.breakEvenCount > 0) Tile("Break-even", metrics.breakEvenCount.toString())
                Tile("Profit factor", ratioOrDash(metrics.profitFactor))
                Tile("Reward : risk", payoffOrDash(metrics.payoffRatio))
                Tile(
                    "Expectancy",
                    moneyOrDash(metrics.expectancy, symbol),
                    valueColor = metrics.expectancy?.let { signColor(it) },
                )
                Tile("Max win streak", metrics.maxWinStreak.toString())
                Tile("Max loss streak", metrics.maxLossStreak.toString())
                Tile("Avg hold", holdOrDash(metrics.avgHoldSeconds))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            ) {
                AppCard(modifier = Modifier.weight(2f), contentFillsHeight = true) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Recent trades")
                        Spacer(Modifier.weight(1f))
                        if (state.recentTrades.isNotEmpty()) {
                            Text(
                                "View all →",
                                color = AppTheme.colors.accent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onViewAllTrades() },
                            )
                        }
                    }
                    RecentTradesList(
                        trades = state.recentTrades,
                        symbol = symbol,
                        onSelect = { onAnalyze(it, state.recentTrades) },
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                    )
                }
                AppCard(modifier = Modifier.weight(1f), contentFillsHeight = true) {
                    TopTradesList(
                        topTrades = state.topTrades,
                        worstTrades = state.worstTrades,
                        symbol = symbol,
                        onSelect = { clicked, group -> onAnalyze(clicked, group) },
                    )
                }
            }

            if (state.tagStats.isNotEmpty()) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("Top tags")
                        Spacer(Modifier.weight(1f))
                        Text(
                            "View reports →",
                            color = AppTheme.colors.accent,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { onOpenReports() },
                        )
                    }
                    TagStatsCompactList(
                        stats = state.tagStats.take(6),
                        symbol = symbol,
                        onSelectTag = onSelectTag,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }
            }
        }
        ColumnVerticalScrollbar(
            scrollState = scrollState,
            // Offset past ScreenScaffold's Spacing.xl content padding so the scrollbar sits flush
            // against the window's right edge instead of floating inside the padding.
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = Spacing.xl).fillMaxHeight(),
        )
    }
}

/**
 * The dashboard's thesis: the running P&L as an oversized ticker readout, sign-colored, paired with
 * the equity curve. Lead with the number that matters before the supporting metric grids below.
 */
@Composable
private fun HeroBlock(state: DashboardState, symbol: String) {
    val metrics = state.metrics
    val context = buildString {
        append(metrics.tradeCount)
        append(if (metrics.tradeCount == 1) " trade" else " trades")
        metrics.winRate?.let { append(" · %.1f%% win rate".format(it * 100)) }
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(200.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        AppCard(modifier = Modifier.weight(1f), contentFillsHeight = true) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Text("NET P&L", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Spacing.sm))
                Text(signedMoney(metrics.netPnl, symbol), style = HeroNumberTextStyle, color = signColor(metrics.netPnl))
                Spacer(Modifier.height(Spacing.sm))
                Text(context, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        AppCard(modifier = Modifier.weight(1.9f), contentFillsHeight = true) {
            SectionLabel("Equity curve")
            EquityCurveChart(
                points = state.equityCurve,
                symbol = symbol,
                modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Tile(label: String, value: String, emphasized: Boolean = false, valueColor: Color? = null) {
    StatCard(label = label, value = value, emphasized = emphasized, valueColor = valueColor, modifier = Modifier.width(150.dp))
}

/** Profit/loss tint for a signed figure; zero reads as profit (break-even leans neutral-positive). */
@Composable
private fun signColor(value: Double): Color =
    if (value >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss

private fun money(v: Double, symbol: String): String = (if (v < 0) "−" else "") + symbol + "%,.2f".format(kotlin.math.abs(v))
private fun moneyOrDash(v: Double?, symbol: String): String = if (v == null) "—" else money(v, symbol)
private fun percentOrDash(v: Double?): String = if (v == null) "—" else "%.1f%%".format(v * 100)
private fun ratioOrDash(v: Double?): String = when {
    v == null -> "—"
    v.isInfinite() -> "∞"
    else -> "%.2f".format(v)
}
private fun payoffOrDash(v: Double?): String = when {
    v == null -> "—"
    v.isInfinite() -> "1 : ∞"
    else -> "1 : %.2f".format(v)
}
private fun holdOrDash(seconds: Double?): String = if (seconds == null) "—" else formatDuration(seconds)
