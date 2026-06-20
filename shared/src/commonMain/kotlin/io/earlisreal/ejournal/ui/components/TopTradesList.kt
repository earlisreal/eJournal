package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Two short lists of individual closed positions: the biggest winners (top trades) and the biggest
 * losers (worst trades). Clicking a row opens that group in Analysis via [onSelect] — the top-trades
 * list for a top row, the worst-trades list for a worst row — with the clicked trade selected.
 */
@Composable
fun TopTradesList(
    topTrades: List<ClosedPosition>,
    worstTrades: List<ClosedPosition>,
    symbol: String,
    onSelect: (clicked: ClosedPosition, group: List<ClosedPosition>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        TradeGroup("Top trades", topTrades, symbol) { onSelect(it, topTrades) }
        TradeGroup("Worst trades", worstTrades, symbol) { onSelect(it, worstTrades) }
    }
}

@Composable
private fun TradeGroup(
    title: String,
    trades: List<ClosedPosition>,
    symbol: String,
    onSelect: (ClosedPosition) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            title.uppercase(),
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (trades.isEmpty()) {
            Text("—", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
        } else {
            trades.forEach { trade ->
                val color = if (trade.profitLoss >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(trade) }
                        .padding(vertical = Spacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            trade.symbol,
                            color = AppTheme.colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            shortDate(trade.exitDatetime.date),
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(signedMoney(trade.profitLoss, symbol), color = color, style = NumberTextStyle)
                }
            }
        }
    }
}
