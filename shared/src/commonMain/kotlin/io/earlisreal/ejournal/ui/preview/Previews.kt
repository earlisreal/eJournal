package io.earlisreal.ejournal.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.SortColumn
import io.earlisreal.ejournal.domain.analytics.SortDirection
import io.earlisreal.ejournal.domain.analytics.computeMetrics
import io.earlisreal.ejournal.domain.analytics.dailySummaries
import io.earlisreal.ejournal.domain.analytics.equityCurve
import io.earlisreal.ejournal.domain.analytics.monthGrid
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.components.AppTextButton
import io.earlisreal.ejournal.ui.components.DateRangeFilter
import io.earlisreal.ejournal.ui.components.DayDetailPanel
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.MonthGrid
import io.earlisreal.ejournal.ui.components.Pill
import io.earlisreal.ejournal.ui.components.SegmentToggle
import io.earlisreal.ejournal.ui.components.StatCard
import io.earlisreal.ejournal.ui.components.TradeLogsTable
import io.earlisreal.ejournal.ui.screen.DashboardContent
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.viewmodel.DashboardState
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import androidx.compose.ui.tooling.preview.Preview

// ---- Sample data (June 2024) -------------------------------------------------------------------

private fun samplePos(
    symbol: String, entry: String, exit: String,
    entryPrice: Double, exitPrice: Double, shares: Double, fees: Double, pnl: Double,
) = ClosedPosition(
    symbol = symbol,
    entryDatetime = LocalDateTime.parse(entry),
    exitDatetime = LocalDateTime.parse(exit),
    averageEntryPrice = entryPrice, averageExitPrice = exitPrice,
    shares = shares, fees = fees, profitLoss = pnl,
)

private val samplePositions = listOf(
    samplePos("AAPL", "2024-06-05T09:18", "2024-06-05T14:30", 182.40, 194.80, 100.0, 12.5, 1240.0),
    samplePos("BDO", "2024-06-02T09:00", "2024-06-08T15:00", 142.10, 143.74, 500.0, 30.0, 820.0),
    samplePos("TSLA", "2024-06-05T10:02", "2024-06-05T11:07", 178.20, 170.45, 40.0, 8.0, -310.0),
    samplePos("SM", "2024-05-20T09:00", "2024-06-03T15:00", 910.0, 937.0, 200.0, 45.0, 540.0),
    samplePos("JFC", "2024-06-10T09:00", "2024-06-10T15:00", 250.0, 244.0, 100.0, 10.0, -600.0),
)

private val sampleMetrics = computeMetrics(samplePositions)
private val sampleDashboardState = DashboardState(
    metrics = sampleMetrics,
    equityCurve = equityCurve(samplePositions),
    recentTrades = samplePositions.sortedByDescending { it.exitDatetime }.take(8),
    topTrades = samplePositions.filter { it.profitLoss > 0.0 }.sortedByDescending { it.profitLoss }.take(5),
    worstTrades = samplePositions.filter { it.profitLoss < 0.0 }.sortedBy { it.profitLoss }.take(5),
)
private val sampleSummaries = dailySummaries(samplePositions)
private val samplePositionsByDay = samplePositions.groupBy { it.exitDatetime.date }
private val sampleGrid = monthGrid(2024, 6)
private val sampleToday = LocalDate(2024, 6, 12)

@Composable
private fun PreviewBox(dark: Boolean = false, content: @Composable () -> Unit) {
    AppTheme(darkTheme = dark) {
        Box(Modifier.background(AppTheme.colors.background).padding(Spacing.lg)) { content() }
    }
}

// ---- Components ---------------------------------------------------------------------------------

@Preview
@Composable
fun ButtonsPreview() = PreviewBox {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppPrimaryButton(text = "Import 42 rows", onClick = {})
        AppSecondaryButton(text = "Select Portfolio", onClick = {})
        AppTextButton(text = "Auto", onClick = {})
        Pill(text = "▾ COL Financial · PHP")
    }
}

@Preview
@Composable
fun StatCardsPreview() = PreviewBox {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        StatCard(label = "Net P&L", value = "+$4,820", emphasized = true, modifier = Modifier.width(160.dp))
        StatCard(label = "Win rate", value = "62%", modifier = Modifier.width(160.dp))
        StatCard(label = "Profit factor", value = "—", modifier = Modifier.width(160.dp))
    }
}

@Preview
@Composable
fun SegmentTogglePreview() = PreviewBox {
    SegmentToggle(segment = Segment.DAY, onSegmentChange = {})
}

@Preview
@Composable
fun DateRangeFilterPreview() = PreviewBox {
    DateRangeFilter(preset = DateRangePreset.THIS_MONTH, customRange = null, onChange = { _, _ -> })
}

@Preview
@Composable
fun EmptyStatePreview() = PreviewBox {
    EmptyState(title = "No closed positions in this range", subtitle = "Adjust the date range or segment.")
}

@Preview
@Composable
fun TradeLogsTablePreview() = PreviewBox {
    TradeLogsTable(
        positions = samplePositions,
        allTags = emptyList(),
        sortColumn = SortColumn.EXIT,
        sortDirection = SortDirection.DESC,
        onSort = {},
        symbol = "$",
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---- Calendar -----------------------------------------------------------------------------------

@Preview
@Composable
fun MonthGridPreview() = PreviewBox {
    Box(Modifier.width(560.dp)) {
        MonthGrid(
            grid = sampleGrid,
            summaries = sampleSummaries,
            today = sampleToday,
            selectedDate = LocalDate(2024, 6, 5),
            onSelectDay = {},
            symbol = "$",
        )
    }
}

@Preview
@Composable
fun DayDetailPanelPreview() = PreviewBox {
    DayDetailPanel(
        date = LocalDate(2024, 6, 5),
        positions = samplePositionsByDay[LocalDate(2024, 6, 5)] ?: emptyList(),
        onAnalyze = { _, _ -> },
        symbol = "$",
        modifier = Modifier.width(300.dp),
    )
}

// ---- Dashboard / Analysis -----------------------------------------------------------------------

@Preview
@Composable
fun DashboardContentPreview() = PreviewBox {
    DashboardContent(state = sampleDashboardState, symbol = "$", onAnalyze = { _, _ -> }, onViewAllTrades = {})
}

@Preview
@Composable
fun DashboardContentDarkPreview() = PreviewBox(dark = true) {
    DashboardContent(state = sampleDashboardState, symbol = "$", onAnalyze = { _, _ -> }, onViewAllTrades = {})
}

@Preview
@Composable
fun AnalysisScreenPreview() = PreviewBox {
    EmptyState(
        title = "No trade selected",
        subtitle = "Open the Calendar or Trade Logs and click a trade to analyze it.",
    )
}
