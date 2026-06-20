package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Right-side navigation list of the positions being analyzed: each row shows the
 * symbol, trade type, and net P/L (calendar-style). The current position is
 * highlighted, the list auto-scrolls to it, and clicking a row navigates there.
 */
@Composable
fun TradesNavList(
    positions: List<ClosedPosition>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    symbol: String,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentIndex) {
        if (currentIndex in positions.indices) listState.animateScrollToItem(currentIndex)
    }

    Column(modifier = modifier.background(AppTheme.colors.surface)) {
        Text(
            "Trades",
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
        HorizontalDivider(color = AppTheme.colors.border)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(positions) { index, p ->
                    TradeNavRow(p, symbol, selected = index == currentIndex) { onSelect(index) }
                    if (index < positions.lastIndex) {
                        HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                    }
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
private fun TradeNavRow(p: ClosedPosition, symbol: String, selected: Boolean, onClick: () -> Unit) {
    val pnlColor = if (p.profitLoss >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AppTheme.colors.accent.copy(alpha = 0.12f) else AppTheme.colors.surface)
            .clickable { onClick() }
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                p.symbol,
                color = if (selected) AppTheme.colors.accent else AppTheme.colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (classifyTradeType(p) == TradeType.DAY) "Day" else "Swing",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Text(signedMoney(p.profitLoss, symbol), color = pnlColor, style = NumberTextStyle)
    }
}
