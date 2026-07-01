package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.domain.PositionTagService
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.marketdata.ChartTimeframe
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.domain.model.TradeDirection
import io.earlisreal.ejournal.domain.model.defaultTagColors
import io.earlisreal.ejournal.ui.chart.CandlestickChart
import io.earlisreal.ejournal.ui.shell.Destination
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.LoadingIndicator
import io.earlisreal.ejournal.ui.components.PositionTransactionsTable
import io.earlisreal.ejournal.ui.components.TagChip
import io.earlisreal.ejournal.ui.components.TagEditorMenu
import io.earlisreal.ejournal.ui.components.TagManagerDialog
import io.earlisreal.ejournal.ui.components.TradesNavList
import io.earlisreal.ejournal.ui.components.formatHold
import io.earlisreal.ejournal.ui.components.signedMoney
import kotlinx.coroutines.launch
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.NumberTextStyle
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.AnalysisViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnalysisScreen(
    positions: List<ClosedPosition>,
    initialIndex: Int,
    marketDataRepository: MarketDataRepository,
    positionTags: PositionTagService,
    tagRepository: TagRepository,
    isDarkTheme: Boolean,
    symbol: String = "$",
    sourceDestination: Destination? = null,
    onBack: (() -> Unit)? = null,
) {
    val vm = viewModel { AnalysisViewModel(marketDataRepository) }
    val state by vm.state.collectAsState()

    LaunchedEffect(positions, initialIndex) { vm.init(positions, initialIndex, isDarkTheme) }
    LaunchedEffect(isDarkTheme) { vm.updateTheme(isDarkTheme) }

    // Tag editing. Positions arrive as a snapshot (already tag-hydrated by the source screen); edits
    // update a per-opening-transaction override so chips refresh without reloading the whole list.
    val tagScope = rememberCoroutineScope()
    var allTags by remember { mutableStateOf<List<Tag>>(emptyList()) }
    var tagOverrides by remember { mutableStateOf<Map<Long, List<Tag>>>(emptyMap()) }
    var showTagManager by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { allTags = tagRepository.getAll() }

    fun currentTagsFor(p: ClosedPosition): List<Tag> =
        p.openingTransactionId?.let { tagOverrides[it] } ?: p.tags

    fun toggleTag(p: ClosedPosition, tag: Tag) {
        val txId = p.openingTransactionId ?: return
        val current = currentTagsFor(p)
        val has = current.any { it.id == tag.id }
        tagOverrides = tagOverrides + (txId to if (has) current.filter { it.id != tag.id } else current + tag)
        tagScope.launch { if (has) positionTags.removeTag(p, tag.id) else positionTags.addTag(p, tag.id) }
    }

    fun createAndAssignTag(p: ClosedPosition, name: String) {
        val txId = p.openingTransactionId ?: return
        tagScope.launch {
            val color = defaultTagColors[allTags.size % defaultTagColors.size]
            val id = try {
                tagRepository.create(name, color)
            } catch (e: Exception) {
                tagRepository.getAll().firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
            } ?: return@launch
            positionTags.addTag(p, id)
            allTags = tagRepository.getAll()
            val created = allTags.firstOrNull { it.id == id } ?: return@launch
            if (created.id !in currentTagsFor(p).map { it.id }) {
                tagOverrides = tagOverrides + (txId to (currentTagsFor(p) + created))
            }
        }
    }

    if (positions.isEmpty()) {
        EmptyState(
            title = "No trade selected",
            subtitle = "Open the Calendar or Trade Logs and click a trade to analyze it.",
        )
        return
    }

    val position = state.position
    val isDay = position?.let { classifyTradeType(it) == TradeType.DAY } ?: false

    // Arrow-key navigation: Up/Left → previous trade, Down/Right → next (both wrap around).
    // The root grabs focus on entry so keys work immediately; clicking the native JCEF/Chromium
    // chart can take focus away, after which interacting with any Compose control restores it.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.DirectionLeft -> { vm.navigatePrev(); true }
                    Key.DirectionDown, Key.DirectionRight -> { vm.navigateNext(); true }
                    else -> false
                }
            },
    ) {

        // ── Breadcrumb ──────────────────────────────────────────────────────
        if (onBack != null && sourceDestination != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppTheme.colors.surface)
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            ) {
                Text(
                    "← ${sourceDestination.label}",
                    color = AppTheme.colors.accent,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.clickable { onBack() },
                )
            }
        }

        // ── Main area: analysis column + trades navigation list ─────────────
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                // ── Header bar ───────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppTheme.colors.surfaceElevated)
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (position != null) {
                        val pnlColor = if (position.profitLoss >= 0) AppTheme.colors.profit else AppTheme.colors.loss
                        val cost = position.averageEntryPrice * position.shares
                        val pct = if (cost != 0.0) position.profitLoss / cost * 100.0 else 0.0
                        Text(
                            position.symbol,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            "${signedMoney(position.profitLoss, symbol)} (${"%+.1f%%".format(pct)})",
                            color = pnlColor,
                            style = NumberTextStyle,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = Spacing.sm),
                        )
                        val side = if (position.direction == TradeDirection.SHORT) "Short" else "Long"
                        Text(
                            "$side · ${if (isDay) "Day" else "Swing"} · ${"%.0f".format(position.shares)} sh",
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Box(modifier = Modifier.weight(1f))
                    val total = state.totalCount
                    val idx   = state.currentIndex
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        NavButton("◀", enabled = total > 1) { vm.navigatePrev() }
                        Text(
                            "${idx + 1} / $total",
                            color = AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        NavButton("▶", enabled = total > 1) { vm.navigateNext() }
                    }
                }

                // ── Tags ─────────────────────────────────────────────────────
                if (position != null && position.openingTransactionId != null) {
                    TagEditRow(
                        tags = currentTagsFor(position),
                        allTags = allTags,
                        onToggle = { toggleTag(position, it) },
                        onCreate = { createAndAssignTag(position, it) },
                        onManage = { showTagManager = true },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }

                // ── Control bar ──────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timeframes = if (isDay)
                        listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN, ChartTimeframe.DAILY, ChartTimeframe.WEEKLY)
                    else
                        listOf(ChartTimeframe.DAILY, ChartTimeframe.WEEKLY)

                    timeframes.forEach { tf ->
                        val isIntraday = tf in listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN)
                        val unavailable = isIntraday && !state.has1MinData
                        val active = state.activeTimeframe == tf
                        Text(
                            tf.label,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .background(
                                    if (active) AppTheme.colors.accent else AppTheme.colors.surfaceElevated,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable(enabled = !unavailable) { vm.selectTimeframe(tf) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = when {
                                unavailable -> AppTheme.colors.textMuted.copy(alpha = 0.4f)
                                active      -> AppTheme.colors.onAccent
                                else        -> AppTheme.colors.textMuted
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Box(modifier = Modifier.weight(1f))

                    if (state.activeTimeframe in listOf(ChartTimeframe.ONE_MIN, ChartTimeframe.FIVE_MIN, ChartTimeframe.FIFTEEN_MIN)) {
                        val vwapOn = state.vwapEnabled
                        Text(
                            "⬤ VWAP",
                            modifier = Modifier
                                .border(1.dp, if (vwapOn) AppTheme.colors.accent else AppTheme.colors.border, RoundedCornerShape(12.dp))
                                .background(
                                    if (vwapOn) AppTheme.colors.accent.copy(alpha = 0.15f) else AppTheme.colors.surface,
                                    RoundedCornerShape(12.dp),
                                )
                                .clickable { vm.toggleVwap() }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            color = if (vwapOn) AppTheme.colors.accent else AppTheme.colors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // ── Chart area ───────────────────────────────────────────────
                // CandlestickChart stays mounted across navigations so the JCEF/Chromium
                // chart isn't torn down and rebuilt on every position change. The loading
                // indicator overlays it instead of replacing it.
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (state.noDataForTimeframe) {
                        EmptyState(
                            title = "No market data",
                            subtitle = "Go to Settings → Sync market data to fetch OHLCV bars for this trade.",
                        )
                    } else {
                        CandlestickChart(state = state, modifier = Modifier.fillMaxSize())
                        if (state.loading) LoadingIndicator()
                    }
                }

                // ── Transactions table ───────────────────────────────────────
                if (position != null && position.transactions.isNotEmpty()) {
                    PositionTransactionsTable(
                        position = position,
                        symbol = symbol,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }

                // ── Summary bar ──────────────────────────────────────────────
                // FlowRow so the stat cells wrap onto a second line when the window is
                // narrow, instead of being squeezed to zero width (which forces the
                // trailing values to wrap one character per line).
                if (position != null) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppTheme.colors.surfaceElevated)
                            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                    ) {
                        val pnlColor = if (position.profitLoss >= 0) AppTheme.colors.profit else AppTheme.colors.loss
                        StatCell("Net P/L", signedMoney(position.profitLoss, symbol), valueColor = pnlColor)
                        StatCell("Avg Entry", "%.2f".format(position.averageEntryPrice))
                        StatCell("Avg Exit",  "%.2f".format(position.averageExitPrice))
                        StatCell("Shares",   "%.0f".format(position.shares))
                        StatCell("Fees",     "%.2f".format(position.fees))
                        StatCell("Entry",    "%02d:%02d:%02d".format(position.entryDatetime.hour, position.entryDatetime.minute, position.entryDatetime.second))
                        StatCell("Exit",     "%02d:%02d:%02d".format(position.exitDatetime.hour,  position.exitDatetime.minute,  position.exitDatetime.second))
                        StatCell("Hold",     formatHold(position.entryDatetime, position.exitDatetime, isDay))
                    }
                }
            }

            VerticalDivider(color = AppTheme.colors.border)

            TradesNavList(
                positions = positions,
                currentIndex = state.currentIndex,
                onSelect = { vm.navigateTo(it) },
                symbol = symbol,
                modifier = Modifier.width(280.dp).fillMaxHeight(),
            )
        }
    }

    if (showTagManager) {
        TagManagerDialog(
            tagRepository = tagRepository,
            onChanged = { tagScope.launch { allTags = tagRepository.getAll() } },
            onDismiss = { showTagManager = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagEditRow(
    tags: List<Tag>,
    allTags: List<Tag>,
    onToggle: (Tag) -> Unit,
    onCreate: (String) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Tags",
            color = AppTheme.colors.textMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(end = 4.dp),
        )
        tags.forEach { TagChip(it, onRemove = { onToggle(it) }) }
        Box {
            Text(
                "＋ Tag",
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                color = AppTheme.colors.accent,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            TagEditorMenu(
                expanded = expanded,
                allTags = allTags,
                selectedIds = tags.mapTo(mutableSetOf()) { it.id },
                onToggle = onToggle,
                onCreate = onCreate,
                onManage = { expanded = false; onManage() },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color = AppTheme.colors.textPrimary) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
        Text(value, color = valueColor, style = NumberTextStyle)
    }
}

@Composable
private fun NavButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .background(AppTheme.colors.surfaceElevated, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        color = if (enabled) AppTheme.colors.textPrimary else AppTheme.colors.textMuted,
        style = MaterialTheme.typography.labelSmall,
    )
}
