package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.chart.CandlestickChart
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.signedMoney
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.AnalysisViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

@Composable
fun AnalysisScreen(
    positions: List<ClosedPosition>,
    initialIndex: Int,
    marketDataRepository: MarketDataRepository,
    isDarkTheme: Boolean,
    symbol: String = "$",
) {
    val vm = viewModel { AnalysisViewModel(marketDataRepository) }
    val state by vm.state.collectAsState()

    LaunchedEffect(positions, initialIndex) { vm.init(positions, initialIndex, isDarkTheme) }
    LaunchedEffect(isDarkTheme) { vm.updateTheme(isDarkTheme) }

    if (positions.isEmpty()) {
        EmptyState(
            title = "No trade selected",
            subtitle = "Open the Calendar or Trade Logs and click a trade to analyze it.",
        )
        return
    }

    val position = state.position
    val isDay = position?.let { classifyTradeType(it) == TradeType.DAY } ?: false

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header bar ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (position != null) {
                val pnlColor = if (position.profitLoss >= 0) AppTheme.colors.profit else AppTheme.colors.loss
                val cost = position.averageEntryPrice * position.shares
                val pct = if (cost != 0.0) position.profitLoss / cost * 100.0 else 0.0
                Text(
                    position.symbol,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary,
                )
                Text(
                    "  ${signedMoney(position.profitLoss, symbol)} (${"%+.1f%%".format(pct)})",
                    color = pnlColor,
                    style = NumberTextStyle,
                    modifier = Modifier.padding(end = Spacing.sm),
                )
                Text(
                    "${if (isDay) "Day" else "Swing"} · ${"%.0f".format(position.shares)} sh",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Box(modifier = Modifier.weight(1f))
            val total = state.totalCount
            val idx   = state.currentIndex
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NavButton("◀", enabled = idx > 0) { vm.navigatePrev() }
                Text(
                    "${idx + 1} / $total",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                NavButton("▶", enabled = idx < total - 1) { vm.navigateNext() }
            }
        }

        // ── Control bar ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val timeframes = if (isDay)
                listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN, ChartTimeframe.DAILY, ChartTimeframe.WEEKLY)
            else
                listOf(ChartTimeframe.DAILY, ChartTimeframe.WEEKLY)

            timeframes.forEach { tf ->
                val active = state.activeTimeframe == tf
                Text(
                    tf.label,
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .background(
                            if (active) AppTheme.colors.accent else AppTheme.colors.surfaceElevated,
                            RoundedCornerShape(4.dp),
                        )
                        .clickable { vm.selectTimeframe(tf) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = if (active) AppTheme.colors.onAccent else AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(modifier = Modifier.weight(1f))

            if (state.activeTimeframe in listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN)) {
                val vwapOn = state.vwapEnabled
                Text(
                    "⬤ VWAP",
                    modifier = Modifier
                        .border(1.dp, if (vwapOn) AppTheme.colors.accent else AppTheme.colors.border, RoundedCornerShape(12.dp))
                        .background(
                            if (vwapOn) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surface,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable { vm.toggleVwap() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    color = if (vwapOn) AppTheme.colors.accent else AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // ── Chart area ───────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.loading -> LoadingIndicator()
                state.noDataForTimeframe -> EmptyState(
                    title = "No market data",
                    subtitle = "Go to Settings → Sync market data to fetch OHLCV bars for this trade.",
                )
                state.chartData != null -> CandlestickChart(state = state, modifier = Modifier.fillMaxSize())
            }
        }

        // ── Summary bar ──────────────────────────────────────────────────────
        if (position != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.surfaceElevated)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                val holdSec = position.exitDatetime.toInstant(TimeZone.UTC).epochSeconds -
                              position.entryDatetime.toInstant(TimeZone.UTC).epochSeconds
                val holdStr = if (isDay) "${holdSec / 3600}h ${(holdSec % 3600) / 60}m" else "${holdSec / 86400}d"
                StatCell("Avg Buy",  "%.2f".format(position.averageEntryPrice))
                StatCell("Avg Sell", "%.2f".format(position.averageExitPrice))
                StatCell("Shares",   "%.0f".format(position.shares))
                StatCell("Fees",     "%.2f".format(position.fees))
                StatCell("Entry",    "%02d:%02d".format(position.entryDatetime.hour, position.entryDatetime.minute))
                StatCell("Exit",     "%02d:%02d".format(position.exitDatetime.hour,  position.exitDatetime.minute))
                StatCell("Hold",     holdStr)
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = AppTheme.colors.textPrimary, style = NumberTextStyle)
    }
}

@Composable
private fun NavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(AppTheme.colors.surfaceElevated, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
        style = MaterialTheme.typography.labelSmall,
    )
}
