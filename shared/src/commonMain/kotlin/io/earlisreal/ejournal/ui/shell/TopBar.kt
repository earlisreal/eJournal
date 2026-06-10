package io.earlisreal.ejournal.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.components.PortfolioSwitcher
import io.earlisreal.ejournal.ui.components.ThemeToggle
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.theme.ThemeMode

@Composable
fun TopBar(
    portfolios: List<Portfolio>,
    selectedPortfolio: Portfolio?,
    onSelectPortfolio: (Portfolio) -> Unit,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = AppTheme.colors.surface
    val borderColor = AppTheme.colors.border
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(surfaceColor)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        PortfolioSwitcher(portfolios = portfolios, selected = selectedPortfolio, onSelect = onSelectPortfolio)
        // Reserved slot for the date-range filter (added in the Trade Logs / Dashboard phase).
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        ThemeToggle(mode = themeMode, onModeChange = onThemeChange)
    }
}
