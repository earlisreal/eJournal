package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlin.math.abs

@Composable
fun GroupedReportTable(
    rows: List<ReportBucket>, symbol: String, metric: ReportMetric?, direction: SortDirection,
    onSort: (ReportMetric?) -> Unit, onSelect: (String) -> Unit, listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().background(AppTheme.colors.surfaceElevated).padding(Spacing.sm)) {
            Text("Group${if (metric == null) " ↑" else ""}", Modifier.weight(1.5f).clickable { onSort(null) }, style = MaterialTheme.typography.labelSmall)
            ReportMetric.entries.forEach { column ->
                val arrow = if (metric != column) "" else if (direction == SortDirection.ASC) " ↑" else " ↓"
                Text(column.label + arrow, Modifier.weight(1f).clickable { onSort(column) }, textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
            }
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(rows, key = { it.key }) { row ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = row.positions.isNotEmpty()) { onSelect(row.key) }.padding(Spacing.sm)) {
                        Text(row.label, Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                        ReportMetric.entries.forEach { column ->
                            val value = column.value(row.metrics)
                            val label = when {
                                value == null -> "—"
                                column == ReportMetric.COUNT -> value.toInt().toString()
                                column == ReportMetric.WIN_RATE -> "%.1f%%".format(value * 100)
                                column == ReportMetric.PROFIT_FACTOR -> if (value.isInfinite()) "∞" else "%.2f".format(value)
                                else -> signedMoney(value, symbol)
                            }
                            Text(label, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    HorizontalDivider(color = AppTheme.colors.border)
                }
            }
            ListVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

/** Fixed-width buckets keep all 48 entry slots legible and keyboard selectable. */
@Composable
fun GroupedReportCharts(rows: List<ReportBucket>, symbol: String, onSelect: (String) -> Unit, listState: LazyListState) {
    val colors = AppTheme.colors
    val maxPnl = rows.maxOfOrNull { abs(it.metrics.netPnl) }?.coerceAtLeast(1.0) ?: 1.0
    val maxCount = rows.maxOfOrNull { it.metrics.tradeCount }?.coerceAtLeast(1) ?: 1
    Text("Net P&L / Position count · scroll for all buckets", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
    LazyRow(Modifier.fillMaxWidth().height(156.dp), state = listState) {
        items(rows, key = { it.key }) { row ->
            val pnl = row.metrics.netPnl
            val count = row.metrics.tradeCount
            Column(Modifier.width(100.dp).clickable(enabled = count > 0) { onSelect(row.key) }
                .semantics { contentDescription = "${row.label}, net P&L $pnl, $count Positions" }.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(compactMoney(pnl, symbol), style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.fillMaxWidth().height(42.dp)) {
                    drawLine(colors.border, Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
                    val h = (abs(pnl) / maxPnl * size.height / 2).toFloat()
                    drawRect(if (pnl >= 0) colors.profit else colors.loss,
                        Offset(size.width * .25f, if (pnl >= 0) size.height / 2 - h else size.height / 2), Size(size.width * .5f, h))
                }
                Text(count.toString(), style = MaterialTheme.typography.labelSmall)
                Canvas(Modifier.fillMaxWidth().height(32.dp)) {
                    val h = count.toFloat() / maxCount * size.height
                    drawRect(colors.textMuted, Offset(size.width * .25f, size.height - h), Size(size.width * .5f, h))
                }
                Text(row.label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
            }
        }
    }
}
