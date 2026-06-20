package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.FifoMatcher
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.LocalDateTime

private data class TxnCol(val title: String, val weight: Float, val numeric: Boolean)

private val TXN_COLUMNS = listOf(
    TxnCol("Time", 1.5f, false),
    TxnCol("Side", 0.6f, false),
    TxnCol("Price", 1.0f, true),
    TxnCol("Shares", 0.9f, true),
    TxnCol("Value", 1.2f, true),
    TxnCol("Fees", 0.8f, true),
    TxnCol("Realized", 1.3f, true),
)

/** Lists the individual fills that make up a [ClosedPosition], in FIFO-matched order. */
@Composable
fun PositionTransactionsTable(position: ClosedPosition, symbol: String, modifier: Modifier = Modifier) {
    val isDay = classifyTradeType(position) == TradeType.DAY
    val realized = remember(position) { FifoMatcher.realizedPnLByTransaction(position) }
    val scrollState = rememberScrollState()
    Column(modifier = modifier.border(1.dp, AppTheme.colors.border, CardShape)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(AppTheme.colors.surfaceElevated)
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        ) {
            TXN_COLUMNS.forEach { col ->
                Text(
                    col.title,
                    modifier = Modifier.weight(col.weight),
                    textAlign = if (col.numeric) TextAlign.End else TextAlign.Start,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(color = AppTheme.colors.border)
        // Caps at ~4 rows; once a trade has more fills the list scrolls (mouse wheel).
        Column(modifier = Modifier.heightIn(max = 104.dp).verticalScroll(scrollState)) {
            position.transactions.forEachIndexed { index, tx ->
                TransactionRow(tx, realized.getOrNull(index), symbol, isDay)
                if (index < position.transactions.lastIndex) {
                    HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun TransactionRow(tx: Transaction, realizedPnL: Double?, symbol: String, isDay: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        Cell(formatTxnTime(tx.datetime, isDay), TXN_COLUMNS[0].weight)
        Cell(if (tx.action == Action.BUY) "Buy" else "Sell", TXN_COLUMNS[1].weight)
        NumCell("%.2f".format(tx.price), TXN_COLUMNS[2].weight)
        NumCell("%.0f".format(tx.shares), TXN_COLUMNS[3].weight)
        NumCell("%,.2f".format(tx.price * tx.shares), TXN_COLUMNS[4].weight)
        NumCell("%.2f".format(tx.fees), TXN_COLUMNS[5].weight)
        if (realizedPnL == null) {
            NumCell("—", TXN_COLUMNS[6].weight, color = AppTheme.colors.textMuted)
        } else {
            val pnlColor = if (realizedPnL >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
            NumCell(signedMoney(realizedPnL, symbol), TXN_COLUMNS[6].weight, color = pnlColor)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, weight: Float) {
    Text(
        text, modifier = Modifier.weight(weight),
        color = AppTheme.colors.textPrimary,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NumCell(
    text: String,
    weight: Float,
    color: Color = AppTheme.colors.textPrimary,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.End,
        color = color,
        style = NumberTextStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Day trades show HH:MM:SS; swing fills span days, so they are prefixed with MM/DD. */
private fun formatTxnTime(dt: LocalDateTime, isDay: Boolean): String {
    val time = "%02d:%02d:%02d".format(dt.hour, dt.minute, dt.second)
    return if (isDay) time else "%02d/%02d %s".format(dt.monthNumber, dt.dayOfMonth, time)
}
