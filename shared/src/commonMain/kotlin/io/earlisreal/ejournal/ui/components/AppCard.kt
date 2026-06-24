package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    contentFillsHeight: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.surface),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        val contentModifier = if (contentFillsHeight) Modifier.fillMaxHeight() else Modifier
        Column(modifier = contentModifier.padding(Spacing.lg)) { content() }
    }
}

/**
 * Label + value stat tile. `emphasized` draws the amber bottom-border accent. Pass [valueColor]
 * to tint the figure by sign (profit/loss) where the sign carries information; defaults to neutral.
 */
@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    valueColor: Color? = null,
) {
    val accent = AppTheme.colors.accent
    AppCard(
        modifier = if (emphasized) {
            modifier.drawBehind {
                val stroke = 3.dp.toPx()
                drawLine(
                    color = accent,
                    start = Offset(0f, size.height - stroke / 2),
                    end = Offset(size.width, size.height - stroke / 2),
                    strokeWidth = stroke,
                )
            }
        } else modifier
    ) {
        Text(label.uppercase(), color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, style = NumberTextStyle, color = valueColor ?: AppTheme.colors.textPrimary)
    }
}
