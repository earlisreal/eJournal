package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.earlisreal.ejournal.domain.analytics.ReportBucket
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ControlShape
import kotlin.math.abs
import kotlin.math.ceil

internal fun reportBucketIndex(x: Float, width: Float, count: Int): Int? =
    if (count <= 0 || width <= 0 || x < 0 || x >= width) null else (x / width * count).toInt().coerceAtMost(count - 1)

/** P&L and count share a full-width domain; pointer or keyboard inspection exposes every bucket. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GroupedReportCharts(rows: List<ReportBucket>, symbol: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = AppTheme.colors
    val measurer = rememberTextMeasurer()
    val axisStyle = MaterialTheme.typography.bodySmall.copy(color = colors.textMuted, fontSize = 11.sp)
    var active by remember(rows) { mutableStateOf<Int?>(null) }
    var focused by remember { mutableStateOf(false) }
    var chartWidth by remember { mutableFloatStateOf(0f) }
    val requester = remember { FocusRequester() }
    val density = LocalDensity.current
    val gutter = with(density) { 58.dp.toPx() }
    val right = with(density) { 20.dp.toPx() }
    val selected = active?.let(rows::getOrNull)
    val maxCount = rows.maxOfOrNull { it.metrics.tradeCount }?.coerceAtLeast(1) ?: 1
    val high = rows.maxOfOrNull { it.metrics.netPnl }?.coerceAtLeast(0.0) ?: 0.0
    val low = rows.minOfOrNull { it.metrics.netPnl }?.coerceAtMost(0.0) ?: 0.0
    val hi = if (high == 0.0 && low == 0.0) 1.0 else high * 1.15
    val lo = if (high == 0.0 && low == 0.0) -1.0 else low * 1.15

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 23.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Net P&L", color = colors.textPrimary, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            if (selected != null) {
                Text("${selected.label}   ${signedMoney(selected.metrics.netPnl, symbol)}   ${selected.metrics.tradeCount} Positions",
                    color = colors.textPrimary, style = MaterialTheme.typography.bodySmall)
            } else Text(if (rows.any { it.positions.isNotEmpty() }) "Point to a bar to inspect. Select to view Positions." else "No Positions in this range.", color = colors.textMuted, style = MaterialTheme.typography.bodySmall)
        }
        Canvas(Modifier.fillMaxWidth().height(164.dp)
            .onSizeChanged { chartWidth = it.width.toFloat() }
            .focusRequester(requester).onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || rows.isEmpty()) false else when (event.key) {
                    Key.DirectionRight -> { active = ((active ?: -1) + 1).coerceAtMost(rows.lastIndex); true }
                    Key.DirectionLeft -> { active = ((active ?: rows.size) - 1).coerceAtLeast(0); true }
                    Key.MoveHome -> { active = 0; true }
                    Key.MoveEnd -> { active = rows.lastIndex; true }
                    Key.Enter, Key.Spacebar -> { active?.let(rows::getOrNull)?.takeIf { it.positions.isNotEmpty() }?.let { onSelect(it.key) }; true }
                    else -> false
                }
            }
            .focusable()
            .then(if (focused) Modifier.border(1.dp, colors.accent, ControlShape) else Modifier)
            .onPointerEvent(PointerEventType.Move) { event ->
                active = reportBucketIndex(event.changes.first().position.x - gutter, chartWidth - gutter - right, rows.size)
            }
            .onPointerEvent(PointerEventType.Exit) { if (!focused) active = null }
            .pointerInput(rows, chartWidth) {
                detectTapGestures { offset ->
                    requester.requestFocus()
                    active = reportBucketIndex(offset.x - gutter, chartWidth - gutter - right, rows.size)
                    active?.let(rows::getOrNull)?.takeIf { it.positions.isNotEmpty() }?.let { onSelect(it.key) }
                }
            }
            .semantics {
                contentDescription = "Net P&L and Position count by group. Use left and right arrows to inspect groups, Enter to view Positions."
                stateDescription = selected?.let { "${it.label}, ${signedMoney(it.metrics.netPnl, symbol)}, ${it.metrics.tradeCount} Positions" } ?: "No group selected"
                customActions = listOf(
                    CustomAccessibilityAction("Previous group") {
                        if (rows.isEmpty()) false else { active = ((active ?: rows.size) - 1).coerceAtLeast(0); true }
                    },
                    CustomAccessibilityAction("Next group") {
                        if (rows.isEmpty()) false else { active = ((active ?: -1) + 1).coerceAtMost(rows.lastIndex); true }
                    },
                )
                onClick(label = "View inspected group") {
                    selected?.takeIf { it.positions.isNotEmpty() }?.let { onSelect(it.key); true } ?: false
                }
            }) {
            if (rows.isEmpty()) return@Canvas
            val left = gutter
            val plotWidth = (size.width - left - right).coerceAtLeast(1f)
            val step = plotWidth / rows.size
            val barWidth = (step * .62f).coerceAtMost(38.dp.toPx())
            val netTop = 7.dp.toPx()
            val netBottom = 88.dp.toPx()
            val countTop = 115.dp.toPx()
            val countBottom = 142.dp.toPx()
            fun y(value: Double) = netTop + ((hi - value) / (hi - lo) * (netBottom - netTop)).toFloat()
            fun label(text: String, x: Float, top: Float) {
                drawText(measurer, text, Offset(x, top), style = axisStyle)
            }
            active?.let { index ->
                drawRoundRect(colors.accent.copy(alpha = .08f), Offset(left + index * step, 0f), Size(step, countBottom + 2.dp.toPx()), CornerRadius(3.dp.toPx()))
            }
            val levels = listOf(high, 0.0, low).distinct()
            levels.forEach { value ->
                val lineY = y(value)
                drawLine(colors.border.copy(alpha = if (value == 0.0) 1f else .5f), Offset(left, lineY), Offset(size.width - right, lineY))
                label(compactMoney(value, symbol), 0f, lineY - 7.dp.toPx())
            }
            label("Positions", 0f, countTop - 17.dp.toPx())
            label(maxCount.toString(), 0f, countTop - 2.dp.toPx())
            label("0", 0f, countBottom - 10.dp.toPx())
            drawLine(colors.border.copy(alpha = .6f), Offset(left, countBottom), Offset(size.width - right, countBottom))
            rows.forEachIndexed { index, bucket ->
                val pnl = bucket.metrics.netPnl
                val center = left + step * (index + .5f)
                val color = if (pnl >= 0) colors.profit else colors.loss
                if (pnl != 0.0) drawRoundRect(color.copy(alpha = if (active == index) 1f else .95f),
                    Offset(center - barWidth / 2, minOf(y(pnl), y(0.0))), Size(barWidth, abs(y(pnl) - y(0.0)).coerceAtLeast(1f)), CornerRadius(2.dp.toPx()))
                val height = bucket.metrics.tradeCount.toFloat() / maxCount * (countBottom - countTop)
                if (height > 0) drawRoundRect((if (active == index) colors.textPrimary else colors.textMuted.copy(alpha = .85f)),
                    Offset(center - barWidth / 2, countBottom - height), Size(barWidth, height), CornerRadius(2.dp.toPx()))
                val every = ceil(rows.size / 7.0).toInt().coerceAtLeast(1)
                val show = if (rows.size == 48 || rows.size == 49) index % 8 == 0 || index == rows.lastIndex else rows.size <= 7 || index % every == 0 || index == rows.lastIndex
                if (show) {
                    val text = when {
                        bucket.unverified -> "Unverified"
                        bucket.key.startsWith("weekday:") -> bucket.label.take(3)
                        bucket.label.length > 13 -> bucket.label.take(12) + "…"
                        else -> bucket.label
                    }
                    val layout = measurer.measure(text, axisStyle)
                    label(text, (center - layout.size.width / 2).coerceIn(left, (size.width - layout.size.width).coerceAtLeast(left)), countBottom + 6.dp.toPx())
                }
            }
        }
    }
}
