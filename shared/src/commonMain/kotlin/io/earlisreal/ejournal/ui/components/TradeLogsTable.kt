package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.SortColumn
import io.earlisreal.ejournal.domain.analytics.SortDirection
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

// column == null → a non-sortable column (Tags).
private data class Col(val column: SortColumn?, val title: String, val weight: Float, val numeric: Boolean)

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
    Col(null, "Tags", 1.8f, false),
)

@Composable
fun TradeLogsTable(
    positions: List<ClosedPosition>,
    allTags: List<Tag>,
    sortColumn: SortColumn,
    sortDirection: SortDirection,
    onSort: (SortColumn) -> Unit,
    symbol: String,
    onAnalyze: (ClosedPosition, List<ClosedPosition>) -> Unit = { _, _ -> },
    onToggleTag: (ClosedPosition, Tag) -> Unit = { _, _ -> },
    onCreateTag: (ClosedPosition, String) -> Unit = { _, _ -> },
    onManageTags: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    androidx.compose.foundation.layout.Column(
        modifier = modifier.border(1.dp, AppTheme.colors.border, CardShape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            COLUMNS.forEach { col ->
                val sortCol = col.column
                val active = sortCol != null && sortCol == sortColumn
                val arrow = if (!active) "" else if (sortDirection == SortDirection.ASC) " ↑" else " ↓"
                Text(
                    text = col.title + arrow,
                    modifier = Modifier.weight(col.weight)
                        .then(if (sortCol != null) Modifier.clickable { onSort(sortCol) } else Modifier),
                    textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(positions) { index, p ->
                    PositionRow(
                        p = p,
                        symbol = symbol,
                        allTags = allTags,
                        onToggleTag = onToggleTag,
                        onCreateTag = onCreateTag,
                        onManageTags = onManageTags,
                        onClick = { onAnalyze(p, positions) },
                    )
                    if (index < positions.lastIndex) HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                }
            }
            ListVerticalScrollbar(
                listState = listState,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun PositionRow(
    p: ClosedPosition,
    symbol: String,
    allTags: List<Tag>,
    onToggleTag: (ClosedPosition, Tag) -> Unit,
    onCreateTag: (ClosedPosition, String) -> Unit,
    onManageTags: () -> Unit,
    onClick: () -> Unit,
) {
    val type = classifyTradeType(p)
    val pnlColor = if (p.profitLoss >= 0) AppTheme.colors.profit else AppTheme.colors.loss
    val cost = p.averageEntryPrice * p.shares
    val pct = if (cost == 0.0) 0.0 else p.profitLoss / cost * 100.0

    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
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
        NumCell(formatSignedMoney(p.profitLoss, symbol), COLUMNS[9].weight, color = pnlColor, bold = true)
        NumCell("%+.1f%%".format(pct), COLUMNS[10].weight, color = pnlColor)
        TagCell(p, COLUMNS[11].weight, allTags, onToggleTag, onCreateTag, onManageTags)
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
private fun androidx.compose.foundation.layout.RowScope.NumCell(
    text: String,
    weight: Float,
    color: androidx.compose.ui.graphics.Color = AppTheme.colors.textPrimary,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.End,
        color = color,
        fontWeight = if (bold) FontWeight.SemiBold else null,
        style = NumberTextStyle,
    )
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
    return formatHeldDuration(seconds, classifyTradeType(p) == TradeType.DAY)
}

/**
 * Held-duration label. Swing trades show whole days; day trades show hours/minutes/seconds with any
 * zero unit omitted (e.g. `34s`, `1m 5s`, `1h 30s`), falling back to `0s` for an instant fill.
 */
internal fun formatHeldDuration(seconds: Long, isDay: Boolean): String {
    if (!isDay) return "${seconds / 86400}d"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    val parts = buildList {
        if (h > 0) add("${h}h")
        if (m > 0) add("${m}m")
        if (s > 0) add("${s}s")
    }
    return if (parts.isEmpty()) "0s" else parts.joinToString(" ")
}
