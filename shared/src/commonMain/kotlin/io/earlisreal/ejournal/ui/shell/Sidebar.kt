package io.earlisreal.ejournal.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

@Composable
fun Sidebar(
    state: SidebarState,
    current: Destination,
    onSelect: (Destination) -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expanded = state == SidebarState.EXPANDED
    val surfaceColor = AppTheme.colors.surface
    val borderColor = AppTheme.colors.border
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) 180.dp else 64.dp)
            .background(surfaceColor)
            .drawBehind {
                drawLine(
                    color = borderColor,
                    start = Offset(size.width, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(vertical = Spacing.md, horizontal = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            horizontalArrangement = if (expanded) Arrangement.SpaceBetween else Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (expanded) Text("eJournal", color = AppTheme.colors.textPrimary, fontWeight = FontWeight.Bold)
            Text(
                if (expanded) "«" else "»",
                color = AppTheme.colors.textMuted,
                modifier = Modifier.clickable { onToggle() }.padding(Spacing.xs),
            )
        }
        Destination.entries.filterNot { it.pinnedBottom }.forEach { dest ->
            NavItem(dest = dest, expanded = expanded, active = dest == current, onClick = { onSelect(dest) })
        }
        Spacer(Modifier.weight(1f))
        Destination.entries.filter { it.pinnedBottom }.forEach { dest ->
            NavItem(dest = dest, expanded = expanded, active = dest == current, onClick = { onSelect(dest) })
        }
    }
}

@Composable
private fun NavItem(
    dest: Destination,
    expanded: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val accent = AppTheme.colors.accent
    val rowModifier = Modifier
        .fillMaxWidth()
        .clickable(enabled = dest.enabled) { onClick() }
        .background(
            if (active) AppTheme.colors.background else androidx.compose.ui.graphics.Color.Transparent,
            RoundedCornerShape(8.dp),
        )
        .let { base ->
            if (active) base.drawBehind {
                val s = 2.5.dp.toPx()
                drawLine(accent, Offset(0f, size.height - s / 2), Offset(size.width, size.height - s / 2), s)
            } else base
        }
        .padding(horizontal = Spacing.sm, vertical = Spacing.sm)

    val color = when {
        !dest.enabled -> AppTheme.colors.textMuted.copy(alpha = 0.4f)
        active -> AppTheme.colors.textPrimary
        else -> AppTheme.colors.textMuted
    }
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
    ) {
        Icon(
            imageVector = dest.icon,
            contentDescription = dest.label,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        if (expanded) {
            Spacer(Modifier.width(Spacing.sm))
            Text(dest.label, color = color, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

private val Destination.icon: ImageVector
    get() = when (this) {
        Destination.DASHBOARD -> Icons.Outlined.Dashboard
        Destination.TRADE_LOGS -> Icons.Outlined.ReceiptLong
        Destination.IMPORT -> Icons.Outlined.FileUpload
        Destination.CALENDAR -> Icons.Outlined.CalendarMonth
        Destination.ANALYSIS -> Icons.Outlined.Analytics
        Destination.SETTINGS -> Icons.Outlined.Settings
    }
