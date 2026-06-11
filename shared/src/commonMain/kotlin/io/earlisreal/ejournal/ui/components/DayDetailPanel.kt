package io.earlisreal.ejournal.ui.components

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
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import kotlinx.datetime.LocalDate

@Composable
fun DayDetailPanel(
    date: LocalDate?,
    positions: List<ClosedPosition>,
    onAnalyze: (ClosedPosition) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier = modifier) {
        if (date == null) {
            Text(
                "Select a day to see its trades",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            Text(
                longDate(date),
                color = AppTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
            if (positions.isEmpty()) {
                Text(
                    "No trades closed this day",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                positions.forEachIndexed { index, p ->
                    val pnlColor = if (p.profitLoss >= 0.0) AppTheme.colors.profit else AppTheme.colors.loss
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAnalyze(p) }
                            .padding(vertical = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(p.symbol, color = AppTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (classifyTradeType(p) == TradeType.DAY) "Day" else "Swing",
                                color = AppTheme.colors.textMuted,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                        Text(signedMoney(p.profitLoss), color = pnlColor, style = NumberTextStyle)
                    }
                    if (index < positions.lastIndex) {
                        HorizontalDivider(color = AppTheme.colors.border.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}
