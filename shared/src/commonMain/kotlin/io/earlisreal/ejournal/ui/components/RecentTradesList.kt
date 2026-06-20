package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Compact, inline list of the most recent closed positions. Each row is clickable and
 * routes to the Analysis chart via [onSelect]. Capped by the caller (no inner scroll).
 */
@Composable
fun RecentTradesList(
    trades: List<ClosedPosition>,
    symbol: String,
    onSelect: (ClosedPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        trades.forEachIndexed { index, p ->
            RecentTradeRow(p, symbol) { onSelect(p) }
            if (index < trades.lastIndex) {
                HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun RecentTradeRow(p: ClosedPosition, symbol: String, onClick: () -> Unit) {
    val pnlColor = if (p.profitLoss >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
    val cost = p.averageEntryPrice * p.shares
    val pct = if (cost != 0.0) p.profitLoss / cost * 100.0 else 0.0
    val side = if (p.direction == TradeDirection.SHORT) "Short" else "Long"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            p.symbol,
            color = AppTheme.colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            shortDate(p.exitDatetime.date),
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            side,
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
        )
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(signedMoney(p.profitLoss, symbol), color = pnlColor, style = NumberTextStyle)
            Text("(${"%+.1f%%".format(pct)})", color = pnlColor, style = MaterialTheme.typography.labelSmall)
        }
        Text("▸", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}
