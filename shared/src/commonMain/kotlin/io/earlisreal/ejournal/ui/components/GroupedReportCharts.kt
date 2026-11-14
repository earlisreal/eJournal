package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
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
fun GroupedReportCharts(
    rows: List<ReportBucket>, symbol: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier,
    showOutcomes: Boolean = false,
) {
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
        Canvas(Modifier.fillMaxWidth().height(if (showOutcomes) 188.dp else 164.dp)
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
                val breakdown = if (showOutcomes) "Position outcomes" else "Position count"
                contentDescription = "Net P&L and $breakdown by group. Use left and right arrows to inspect groups, Enter to view Positions."
                stateDescription = selected?.let { bucket ->
                    val m = bucket.metrics
                    val summary = "${bucket.label}, ${signedMoney(m.netPnl, symbol)}, ${m.tradeCount} Positions"
                    if (showOutcomes) "$summary, Winners ${m.winCount}, Losers ${m.lossCount}, Neutral ${m.neutralCount}" else summary
                } ?: "No group selected"
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
            val countBottom = if (showOutcomes) 166.dp.toPx() else 142.dp.toPx()
            val countScale = (countBottom - countTop) / maxCount
            val priceLabelStride = if (showOutcomes) {
                val labelWidth = rows.maxOf { measurer.measure(it.label, axisStyle).size.width }
                ceil((labelWidth + 8.dp.toPx()) / step).toInt().coerceAtLeast(1)
            } else 1
            val every = ceil(rows.size / 7.0).toInt().coerceAtLeast(1)
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
                val money = if (showOutcomes && value != 0.0 && abs(value) < 10.0) signedMoney(value, symbol)
                    else compactMoney(value, symbol)
                label(money, 0f, lineY - 7.dp.toPx())
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
                val leftEdge = center - barWidth / 2
                if (showOutcomes) {
                    val m = bucket.metrics
                    var bottom = countBottom
                    for ((count, outcomeColor) in listOf(m.winCount to colors.profit, m.lossCount to colors.loss, m.neutralCount to colors.textMuted)) {
                        if (count == 0) continue
                        val height = count * countScale
                        bottom -= height
                        drawRect(outcomeColor, Offset(leftEdge, bottom), Size(barWidth, height))
                    }
                } else {
                    val height = bucket.metrics.tradeCount * countScale
                    if (height > 0) drawRoundRect(if (active == index) colors.textPrimary else colors.textMuted.copy(alpha = .85f),
                        Offset(leftEdge, countBottom - height), Size(barWidth, height), CornerRadius(2.dp.toPx()))
                }
                val show = when {
                    index == rows.lastIndex -> true
                    showOutcomes -> index % priceLabelStride == 0 && rows.lastIndex - index >= priceLabelStride
                    rows.size == 48 || rows.size == 49 -> index % 8 == 0
                    else -> index % every == 0
                }
                if (show) {
                    val text = when {
                        showOutcomes -> bucket.label
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
        if (showOutcomes) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
                val m = selected?.metrics
                OutcomeLegend("Winners", m?.winCount, colors.profit)
                OutcomeLegend("Losers", m?.lossCount, colors.loss)
                OutcomeLegend("Neutral", m?.neutralCount, colors.textMuted)
            }
        }
    }
}

@Composable
private fun OutcomeLegend(label: String, count: Int?, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color))
        Text(if (count == null) label else "$label $count", color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
    }
}
