package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

private val DOW = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
private val CellShape = RoundedCornerShape(8.dp)

@Composable
fun MonthGrid(
    grid: List<LocalDate?>,
    summaries: Map<LocalDate, DaySummary>,
    today: LocalDate,
    selectedDate: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            DOW.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        val padded = grid + List((7 - grid.size % 7) % 7) { null }
        padded.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                week.forEach { date ->
                    DayCell(
                        date = date,
                        summary = date?.let { summaries[it] },
                        isToday = date == today,
                        isSelected = date != null && date == selectedDate,
                        onClick = { if (date != null) onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    summary: DaySummary?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (date == null) {
        Box(modifier.aspectRatio(1.3f)) {}
        return
    }
    val hasTrades = summary != null && summary.tradeCount > 0
    val tint = when {
        summary == null -> Color.Transparent
        summary.netPnl > 0 -> AppTheme.colors.profit.copy(alpha = 0.12f)
        summary.netPnl < 0 -> AppTheme.colors.loss.copy(alpha = 0.12f)
        else -> AppTheme.colors.surfaceElevated
    }
    val pnlColor = if ((summary?.netPnl ?: 0.0) >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
    val borderColor = when {
        isSelected -> AppTheme.colors.accent
        isToday -> AppTheme.colors.accent.copy(alpha = 0.5f)
        else -> AppTheme.colors.border
    }
    Column(
        modifier = modifier
            .aspectRatio(1.3f)
            .clip(CellShape)
            .background(tint)
            .border(if (isSelected || isToday) 1.5.dp else 1.dp, borderColor, CellShape)
            .then(if (hasTrades) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = Spacing.xs, vertical = 4.dp),
    ) {
        Text(
            date.dayOfMonth.toString(),
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        if (summary != null) {
            Spacer(Modifier.weight(1f))
            Text(
                signedMoney(summary.netPnl),
                color = pnlColor,
                style = NumberTextStyle,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (summary.tradeCount == 1) "1 trade" else "${summary.tradeCount} trades",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
