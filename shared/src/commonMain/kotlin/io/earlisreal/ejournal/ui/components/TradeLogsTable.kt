package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.SortColumn
import io.earlisreal.ejournal.domain.analytics.SortDirection
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

private data class Col(val column: SortColumn, val title: String, val weight: Float, val numeric: Boolean)

private val COLUMNS = listOf(
    Col(SortColumn.SYMBOL, "Symbol", 1.0f, false),
    Col(SortColumn.TYPE, "Type", 0.8f, false),
    Col(SortColumn.ENTRY, "Entry", 1.2f, false),
    Col(SortColumn.EXIT, "Exit", 1.2f, false),
    Col(SortColumn.HELD, "Held", 0.8f, false),
    Col(SortColumn.SHARES, "Shares", 0.8f, true),
    Col(SortColumn.AVG_ENTRY, "Avg entry", 1.0f, true),
    Col(SortColumn.AVG_EXIT, "Avg exit", 1.0f, true),
    Col(SortColumn.FEES, "Fees", 0.8f, true),
    Col(SortColumn.PNL, "P&L", 1.0f, true),
    Col(SortColumn.PNL_PCT, "P&L %", 0.8f, true),
)

@Composable
fun TradeLogsTable(
    positions: List<ClosedPosition>,
    sortColumn: SortColumn,
    sortDirection: SortDirection,
    onSort: (SortColumn) -> Unit,
    symbol: String,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.border(1.dp, AppTheme.colors.border, CardShape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            COLUMNS.forEach { col ->
                val active = col.column == sortColumn
                val arrow = if (!active) "" else if (sortDirection == SortDirection.ASC) " ↑" else " ↓"
                Text(
                    text = col.title + arrow,
                    modifier = Modifier.weight(col.weight).clickable { onSort(col.column) },
                    textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        LazyColumn {
            itemsIndexed(positions) { index, p ->
                PositionRow(p, symbol, onClick = { onAnalyze(p, positions) })
                if (index < positions.lastIndex) HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PositionRow(p: ClosedPosition, symbol: String, onClick: () -> Unit) {
    val type = classifyTradeType(p)
    val pnlColor = if (p.profitLoss >= 0) AppTheme.colors.profit else AppTheme.colors.loss
    val cost = p.averageEntryPrice * p.shares
    val pct = if (cost == 0.0) 0.0 else p.profitLoss / cost * 100.0

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        SymbolCell(p, COLUMNS[0].weight)
        Box(COLUMNS[1].weight) { TypeBadge(type) }
        Cell(formatDateTime(p.entryDatetime), COLUMNS[2].weight)
        Cell(formatDateTime(p.exitDatetime), COLUMNS[3].weight)
        Cell(formatHeld(p), COLUMNS[4].weight)
        NumCell("%.0f".format(p.shares), COLUMNS[5].weight)
        NumCell("%.2f".format(p.averageEntryPrice), COLUMNS[6].weight)
        NumCell("%.2f".format(p.averageExitPrice), COLUMNS[7].weight)
        NumCell("%.2f".format(p.fees), COLUMNS[8].weight)
        NumCell(formatSignedMoney(p.profitLoss, symbol), COLUMNS[9].weight, color = pnlColor)
        NumCell("%+.1f%%".format(pct), COLUMNS[10].weight, color = pnlColor)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SymbolCell(p: ClosedPosition, weight: Float) {
    Row(
        modifier = Modifier.weight(weight),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) {
        Text(
            p.symbol,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
        )
        if (p.direction == TradeDirection.SHORT) {
            Text(
                "S",
                color = AppTheme.colors.loss,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(AppTheme.colors.loss.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float, bold: Boolean = false) {
    Text(
        text, modifier = Modifier.weight(weight),
        color = AppTheme.colors.textPrimary,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumCell(text: String, weight: Float, color: androidx.compose.ui.graphics.Color = AppTheme.colors.textPrimary) {
    Text(text, modifier = Modifier.weight(weight), textAlign = TextAlign.End, color = color, style = NumberTextStyle)
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Box(weight: Float, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.weight(weight)) { content() }
}

@Composable
private fun TypeBadge(type: TradeType) {
    val (bg, fg) = if (type == TradeType.DAY) AppTheme.colors.accent.copy(alpha = 0.15f) to AppTheme.colors.accent
    else AppTheme.colors.border to AppTheme.colors.textMuted
    Text(
        if (type == TradeType.DAY) "Day" else "Swing",
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(bg, androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

private fun formatSignedMoney(v: Double, symbol: String): String =
    (if (v < 0) "−" else "+") + symbol + "%,.2f".format(kotlin.math.abs(v))

private fun formatDateTime(dt: kotlinx.datetime.LocalDateTime): String {
    val mm = dt.monthNumber.toString().padStart(2, '0')
    val dd = dt.dayOfMonth.toString().padStart(2, '0')
    val hh = dt.hour.toString().padStart(2, '0')
    val min = dt.minute.toString().padStart(2, '0')
    return "$mm/$dd $hh:$min"
}

private fun formatHeld(p: ClosedPosition): String {
    val seconds = p.exitDatetime.toInstant(TimeZone.UTC).epochSeconds - p.entryDatetime.toInstant(TimeZone.UTC).epochSeconds
    return if (classifyTradeType(p) == TradeType.DAY) {
        val h = seconds / 3600; val m = (seconds % 3600) / 60
        "${h}h ${m}m"
    } else {
        "${seconds / 86400}d"
    }
}
