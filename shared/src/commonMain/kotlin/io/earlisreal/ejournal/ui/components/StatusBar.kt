package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.earlisreal.ejournal.background.BackgroundTask
import io.earlisreal.ejournal.background.StatusKind
import io.earlisreal.ejournal.background.TaskState
import io.earlisreal.ejournal.background.summarize
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

/**
 * Persistent footer summarising all background work. Collapsed it shows one line (with a
 * Retry when something failed); clicking expands a panel listing every task. The panel is a
 * [Popup] floating directly above the bar, so it overlays the content instead of resizing it.
 */
@Composable
fun StatusBar(tasks: List<BackgroundTask>, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val summary = summarize(tasks)
    val hasTasks = tasks.isNotEmpty()
    val borderColor = AppTheme.colors.border

    Box(modifier = modifier.fillMaxWidth().background(AppTheme.colors.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .drawBehind {
                    drawLine(borderColor, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                }
                .let { if (hasTasks) it.clickable { expanded = !expanded } else it }
                .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatusGlyph(summary.kind)
            Text(summary.text, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            summary.retry?.let { RetryLink(it) }
            Spacer(Modifier.weight(1f))
            if (hasTasks) {
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                    contentDescription = if (expanded) "Collapse background tasks" else "Expand background tasks",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        if (expanded && hasTasks) {
            Popup(
                popupPositionProvider = AbovePopupPositionProvider,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                StatusPanel(tasks)
            }
        }
    }
}

/** Positions a popup flush above its anchor (the bar), left-aligned. */
private object AbovePopupPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset = IntOffset(
        x = anchorBounds.left,
        y = (anchorBounds.top - popupContentSize.height).coerceAtLeast(0),
    )
}

@Composable
private fun StatusPanel(tasks: List<BackgroundTask>) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = AppTheme.colors.surface,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        border = BorderStroke(1.dp, AppTheme.colors.border),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            tasks.forEach { TaskRow(it) }
        }
    }
}

@Composable
private fun TaskRow(task: BackgroundTask) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        StatusGlyph(task.state.toKind())
        Text(task.label, color = AppTheme.colors.textPrimary, style = MaterialTheme.typography.bodySmall)
        task.detail?.let { Text(it, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.weight(1f))
        if (task.state is TaskState.Failed) task.retry?.let { RetryLink(it) }
    }
}

@Composable
private fun RetryLink(onClick: () -> Unit) {
    Text(
        text = "Retry",
        color = AppTheme.colors.accent,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.clickable { onClick() }.padding(horizontal = Spacing.xs),
    )
}

@Composable
private fun StatusGlyph(kind: StatusKind, size: Dp = 14.dp) {
    when (kind) {
        StatusKind.Running -> CircularProgressIndicator(
            color = AppTheme.colors.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(size),
        )
        StatusKind.Success -> Icon(Icons.Outlined.CheckCircle, null, tint = AppTheme.colors.profit, modifier = Modifier.size(size))
        StatusKind.Failed -> Icon(Icons.Outlined.ErrorOutline, null, tint = AppTheme.colors.loss, modifier = Modifier.size(size))
        StatusKind.Idle -> Icon(Icons.Outlined.CheckCircle, null, tint = AppTheme.colors.textMuted, modifier = Modifier.size(size))
    }
}

private fun TaskState.toKind(): StatusKind = when (this) {
    is TaskState.Running -> StatusKind.Running
    TaskState.Success -> StatusKind.Success
    TaskState.Failed -> StatusKind.Failed
}
