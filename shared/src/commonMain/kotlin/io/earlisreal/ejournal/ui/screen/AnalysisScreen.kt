package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.components.signedMoney
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Placeholder for Phase 8. Shows the selected trade's details; the candlestick chart (which needs
 * Phase 7 market data) arrives in Phase 8 — this navigation + hand-off stays.
 */
@Composable
fun AnalysisScreen(position: ClosedPosition?, symbol: String = "$") {
    ScreenScaffold(title = "Trade Analysis") {
        if (position == null) {
            EmptyState(
                title = "No trade selected",
                subtitle = "Open the Calendar, click a day, then click a trade to analyze it.",
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Text(
                    position.symbol,
                    color = AppTheme.colors.textPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                AppCard {
                    DetailRow("Type", if (classifyTradeType(position) == TradeType.DAY) "Day trade" else "Swing")
                    DetailRow("Entry", position.entryDatetime.toString())
                    DetailRow("Exit", position.exitDatetime.toString())
                    DetailRow("Shares", "%.0f".format(position.shares))
                    DetailRow("Avg entry", "%.2f".format(position.averageEntryPrice))
                    DetailRow("Avg exit", "%.2f".format(position.averageExitPrice))
                    DetailRow("Fees", "%.2f".format(position.fees))
                    DetailRow("P&L", signedMoney(position.profitLoss, symbol))
                }
                Text(
                    "Price chart arrives in Phase 8 (needs market data).",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = AppTheme.colors.textPrimary, style = NumberTextStyle)
    }
}
