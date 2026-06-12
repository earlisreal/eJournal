package io.earlisreal.ejournal.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.domain.analytics.DateRange
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.components.DateRangeFilter
import io.earlisreal.ejournal.ui.components.PortfolioSwitcher
import io.earlisreal.ejournal.ui.components.SegmentToggle
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

@Composable
fun TopBar(
    portfolios: List<Portfolio>,
    selectedPortfolio: Portfolio?,
    onSelectPortfolio: (Portfolio) -> Unit,
    preset: DateRangePreset,
    customRange: DateRange?,
    onDateChange: (DateRangePreset, DateRange?) -> Unit,
    segment: Segment,
    onSegmentChange: (Segment) -> Unit,
    showDateFilter: Boolean,
    onManagePortfolios: () -> Unit,
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
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            }
            .padding(horizontal = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PortfolioSwitcher(
            portfolios = portfolios,
            selected = selectedPortfolio,
            onSelect = onSelectPortfolio,
            onManage = onManagePortfolios,
        )
        if (showDateFilter) {
            DateRangeFilter(preset = preset, customRange = customRange, onChange = onDateChange)
            Spacer(Modifier.width(Spacing.lg))
        }
        SegmentToggle(segment = segment, onSegmentChange = onSegmentChange)
    }
}
