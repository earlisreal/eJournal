package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.TagStat
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

/** Full per-tag statistics table for the Reports screen. Clicking a tagged row calls [onSelectTag]. */
@Composable
fun TagStatsTable(
    stats: List<TagStat>,
    symbol: String,
    onSelectTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Column(modifier.border(1.dp, AppTheme.colors.border, CardShape)) {
        Row(
            Modifier.fillMaxWidth().background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        ) {
            HeaderCell("Tag", 2.0f, TextAlign.Start)
            HeaderCell("Trades", 0.8f, TextAlign.End)
            HeaderCell("Net P&L", 1.2f, TextAlign.End)
            HeaderCell("Win rate", 1.0f, TextAlign.End)
            HeaderCell("Profit factor", 1.2f, TextAlign.End)
            HeaderCell("Avg win", 1.0f, TextAlign.End)
            HeaderCell("Avg loss", 1.0f, TextAlign.End)
        }
        HorizontalDivider(color = AppTheme.colors.border)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(stats) { index, s ->
                    TagStatRow(s, symbol, onSelectTag)
                    if (index < stats.lastIndex) HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                }
            }
            ListVerticalScrollbar(listState = listState, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight())
        }
    }
}

@Composable
private fun TagStatRow(s: TagStat, symbol: String, onSelectTag: (Long) -> Unit) {
    val m = s.metrics
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = s.tag != null) { s.tag?.let { onSelectTag(it.id) } }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(2.0f)) {
            val tag = s.tag
            if (tag != null) TagChip(tag)
            else Text("Untagged", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        NumCell(m.tradeCount.toString(), 0.8f)
        NumCell(signedMoney(m.netPnl, symbol), 1.2f, color = signColor(m.netPnl), bold = true)
        NumCell(percentOrDash(m.winRate), 1.0f)
        NumCell(ratioOrDash(m.profitFactor), 1.2f)
        NumCell(moneyOrDash(m.avgWin, symbol), 1.0f, color = AppTheme.colors.profit)
        NumCell(moneyOrDash(m.avgLoss, symbol), 1.0f, color = AppTheme.colors.loss)
    }
}

/** Compact top-tags list for the Dashboard card: chip + net P&L, with win rate / count as context. */
@Composable
fun TagStatsCompactList(
    stats: List<TagStat>,
    symbol: String,
    onSelectTag: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        stats.forEach { s ->
            val m = s.metrics
            Row(
                Modifier.fillMaxWidth()
                    .clickable(enabled = s.tag != null) { s.tag?.let { onSelectTag(it.id) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val tag = s.tag
                if (tag != null) TagChip(tag)
                else Text("Untagged", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                Text(
                    "  ${percentOrDash(m.winRate)} · ${m.tradeCount}",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                )
                Box(Modifier.weight(1f))
                Text(
                    signedMoney(m.netPnl, symbol),
                    color = signColor(m.netPnl),
                    style = NumberTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun RowScope.HeaderCell(title: String, weight: Float, align: TextAlign) {
    Text(
        title,
        modifier = Modifier.weight(weight),
        textAlign = align,
        color = AppTheme.colors.textMuted,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelSmall,
    )
}

@Composable
private fun RowScope.NumCell(text: String, weight: Float, color: Color = AppTheme.colors.textPrimary, bold: Boolean = false) {
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
private fun signColor(v: Double): Color = if (v >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss

private fun percentOrDash(v: Double?): String = if (v == null) "—" else "%.1f%%".format(v * 100)

private fun ratioOrDash(v: Double?): String = when {
    v == null -> "—"
    v.isInfinite() -> "∞"
    else -> "%.2f".format(v)
}

private fun moneyOrDash(v: Double?, symbol: String): String = if (v == null) "—" else signedMoney(v, symbol)
