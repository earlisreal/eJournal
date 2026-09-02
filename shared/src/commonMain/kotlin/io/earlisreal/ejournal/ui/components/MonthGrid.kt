package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.earlisreal.ejournal.domain.analytics.DaySummary
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.LocalDate

private val DOW = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private val CellShape = RoundedCornerShape(8.dp)

@Composable
fun MonthGrid(
    grid: List<LocalDate>,
    summaries: Map<LocalDate, DaySummary>,
    displayedYear: Int,
    displayedMonth: Int,
    today: LocalDate,
    selectedDate: LocalDate?,
    selectedWeekStart: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    onSelectWeek: (LocalDate) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            (DOW + "Week P&L").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        grid.chunked(7).forEach { week ->
            val weekPnl = week.sumOf { summaries[it]?.netPnl ?: 0.0 }
            val hasPositions = week.any { (summaries[it]?.tradeCount ?: 0) > 0 }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        summary = summaries[date],
                        isAdjacent = date.year != displayedYear || date.monthNumber != displayedMonth,
                        isToday = date == today,
                        isSelected = date == selectedDate,
                        onClick = { onSelectDay(date) },
                        symbol = symbol,
                        modifier = Modifier.weight(1f),
                    )
                }
                WeekPnlCell(
                    pnl = weekPnl,
                    hasPositions = hasPositions,
                    isSelected = week.firstOrNull() == selectedWeekStart,
                    onClick = { if (hasPositions) onSelectWeek(week.first()) },
                    symbol = symbol,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    summary: DaySummary?,
    isAdjacent: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    val netPnl = summary?.netPnl ?: 0.0
    val tradeCount = summary?.tradeCount ?: 0
    val hasTrades = tradeCount > 0
    val pnlColor = if (netPnl >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
    // Days with trades carry the content, so they get a tinted fill, a sign-colored edge, and a
    // bold figure. Empty days recede: no fill, a faint hairline, and a dimmed date number — the
    // eye lands on the days that actually happened.
    val tint = when {
        !hasTrades -> Color.Transparent
        netPnl > 0 -> AppTheme.colors.profit.copy(alpha = 0.14f)
        netPnl < 0 -> AppTheme.colors.loss.copy(alpha = 0.14f)
        else -> AppTheme.colors.surfaceElevated
    }
    val borderColor = when {
        isSelected -> AppTheme.colors.accent
        isToday -> AppTheme.colors.accent.copy(alpha = 0.5f)
        hasTrades -> pnlColor.copy(alpha = 0.35f)
        else -> AppTheme.colors.border.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(CellShape)
            .background(tint)
            .border(if (isSelected || isToday) 1.5.dp else 1.dp, borderColor, CellShape)
            .then(if (hasTrades) Modifier.clickable { onClick() } else Modifier)
            .then(if (isAdjacent) Modifier.alpha(0.6f) else Modifier)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = if (hasTrades) AppTheme.colors.textPrimary else AppTheme.colors.textMuted.copy(alpha = 0.55f),
            fontWeight = if (hasTrades) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelSmall,
        )
        if (hasTrades) {
            Spacer(Modifier.weight(1f))
            Text(
                signedMoney(netPnl, symbol),
                color = pnlColor,
                style = NumberTextStyle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (tradeCount == 1) "1 trade" else "$tradeCount trades",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun WeekPnlCell(
    pnl: Double,
    hasPositions: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    val pnlColor = if (pnl >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
    val tint = when {
        !hasPositions -> Color.Transparent
        pnl > 0.0 -> AppTheme.colors.profit.copy(alpha = 0.14f)
        pnl < 0.0 -> AppTheme.colors.loss.copy(alpha = 0.14f)
        else -> AppTheme.colors.surfaceElevated
    }
    val borderColor = when {
        isSelected -> AppTheme.colors.accent
        hasPositions -> pnlColor.copy(alpha = 0.35f)
        else -> AppTheme.colors.border.copy(alpha = 0.4f)
    }
    Column(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(CellShape)
            .background(tint)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, CellShape)
            .then(if (hasPositions) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            if (hasPositions) signedMoney(pnl, symbol) else "—",
            color = if (hasPositions) pnlColor else AppTheme.colors.textMuted,
            style = NumberTextStyle,
            fontSize = 12.sp,
            fontWeight = if (hasPositions) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
