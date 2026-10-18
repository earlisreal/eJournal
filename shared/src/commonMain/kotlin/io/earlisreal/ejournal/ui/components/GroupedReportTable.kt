package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.earlisreal.ejournal.domain.analytics.*
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlin.math.abs

private val metricWeights = listOf(.65f, 1.25f, 1.15f, .95f, 1.15f, 1.15f, 1.0f)

@Composable
fun GroupedReportTable(
    rows: List<ReportBucket>, symbol: String, metric: ReportMetric?, direction: SortDirection,
    onSort: (ReportMetric?) -> Unit, onSelect: (String) -> Unit, listState: LazyListState,
    modifier: Modifier = Modifier,
    horizontal: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val maximum = rows.maxOfOrNull { abs(it.metrics.netPnl) }?.takeIf { it > 0 } ?: 1.0
    BoxWithConstraints(modifier.clip(CardShape).border(1.dp, AppTheme.colors.border, CardShape)) {
        val availableWidth = maxWidth
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).horizontalScroll(horizontal).width(maxOf(availableWidth, 980.dp))) {
                Row(Modifier.fillMaxWidth().background(AppTheme.colors.surface).padding(horizontal = Spacing.lg)) {
                    SortHeading("Group", metric == null, SortDirection.ASC, { onSort(null) }, Modifier.weight(1.65f), numeric = false)
                    ReportMetric.entries.forEachIndexed { index, column ->
                        SortHeading(column.label, metric == column, direction, { onSort(column) }, Modifier.weight(metricWeights[index]))
                    }
                }
                HorizontalDivider(color = AppTheme.colors.border)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(rows, key = { it.key }) { row ->
                            val interaction = remember { MutableInteractionSource() }
                            val hovered by interaction.collectIsHoveredAsState()
                            val focused by interaction.collectIsFocusedAsState()
                            val colors = AppTheme.colors
                            val populated = row.positions.isNotEmpty()
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (hovered || focused) colors.accent.copy(alpha = .08f) else colors.surface)
                                    .hoverable(interaction)
                                    .clickable(interactionSource = interaction, indication = null, enabled = populated,
                                        role = Role.Button, onClickLabel = "View ${row.label} Positions") { onSelect(row.key) }
                                    .then(if (focused) Modifier.border(1.dp, colors.accent) else Modifier)
                                    .padding(horizontal = Spacing.lg).height(43.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(Modifier.weight(1.65f).padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(row.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (populated) colors.textPrimary else colors.textMuted,
                                        fontWeight = if (row.unverified) FontWeight.SemiBold else FontWeight.Medium,
                                        style = MaterialTheme.typography.bodySmall)
                                    if (populated) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(15.dp), tint = if (hovered || focused) colors.accent else colors.textMuted)
                                }
                                ReportMetric.entries.forEachIndexed { index, column ->
                                    val value = column.value(row.metrics)
                                    val label = when {
                                        value == null -> "—"
                                        column == ReportMetric.COUNT -> value.toInt().toString()
                                        column == ReportMetric.WIN_RATE -> "%.1f%%".format(value * 100)
                                        column == ReportMetric.PROFIT_FACTOR -> if (value.isInfinite()) "∞" else "%.2f".format(value)
                                        else -> signedMoney(value, symbol)
                                    }
                                    val financial = column in setOf(ReportMetric.NET_PNL, ReportMetric.AVERAGE, ReportMetric.WINNER, ReportMetric.LOSER)
                                    val color = when {
                                        !populated || value == null -> colors.textMuted
                                        financial && value > 0 -> colors.profit
                                        financial && value < 0 -> colors.loss
                                        else -> colors.textPrimary
                                    }
                                    Text(label, Modifier.weight(metricWeights[index]).padding(start = 8.dp)
                                        .drawBehind {
                                            if (column == ReportMetric.NET_PNL && populated && row.metrics.netPnl != 0.0) {
                                                val width = (abs(row.metrics.netPnl) / maximum * size.width).toFloat()
                                                drawRoundRect(color.copy(alpha = .18f), Offset(size.width - width, size.height + 5.dp.toPx()), Size(width, 2.dp.toPx()), CornerRadius(2.dp.toPx()))
                                            }
                                        }, textAlign = TextAlign.End, maxLines = 1,
                                        color = color, style = NumberTextStyle.copy(fontSize = 12.sp),
                                        fontWeight = if (column == ReportMetric.NET_PNL) FontWeight.SemiBold else FontWeight.Normal)
                                }
                            }
                            HorizontalDivider(color = AppTheme.colors.border.copy(alpha = .55f))
                        }
                    }
                    ListVerticalScrollbar(listState, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                }
            }
            if (availableWidth < 980.dp) RowHorizontalScrollbar(horizontal, Modifier.fillMaxWidth().height(8.dp))
        }
    }
}

@Composable
private fun SortHeading(label: String, active: Boolean, direction: SortDirection, onClick: () -> Unit, modifier: Modifier, numeric: Boolean = true) {
    val colors = AppTheme.colors
    Row(modifier.height(44.dp).clickable(role = Role.Button, onClickLabel = "Sort by $label", onClick = onClick)
        .semantics { stateDescription = if (active) if (direction == SortDirection.ASC) "Ascending" else "Descending" else "Not sorted" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (numeric) Arrangement.End else Arrangement.Start) {
        Text(label, color = if (active) colors.accent else colors.textMuted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        if (active) Icon(if (direction == SortDirection.ASC) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.padding(start = 3.dp).size(12.dp), tint = colors.accent)
    }
}
