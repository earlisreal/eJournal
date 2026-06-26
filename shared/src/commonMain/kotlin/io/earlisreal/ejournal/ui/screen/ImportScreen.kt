package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncOutcome
import io.earlisreal.ejournal.domain.tradezero.TradeZeroSyncService
import io.earlisreal.ejournal.ui.components.AppCard
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.components.DataTable
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.ErrorBanner
import io.earlisreal.ejournal.ui.components.Pill
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.platform.pickImportFiles
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.CardShape
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.ImportStatus
import io.earlisreal.ejournal.ui.viewmodel.ImportViewModel
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImportScreen(
    transactionRepository: TransactionRepository,
    parsers: List<TransactionParser>,
    portfolioSettings: PortfolioSettingsRepository,
    filter: FilterState,
    onImportSuccess: () -> Unit,
    tradeZeroSyncService: TradeZeroSyncService,
    tradeZeroConfigured: Boolean,
) {
    val vm = viewModel { ImportViewModel(transactionRepository, parsers, portfolioSettings) }
    val state by vm.state.collectAsState()

    var isDragHovered by remember { mutableStateOf(false) }
    val portfolio = filter.portfolio

    ScreenScaffold(title = "Import Transactions") {
        if (portfolio == null) {
            EmptyState(
                title = "No portfolio selected",
                subtitle = "Create or pick a portfolio from the switcher in the top bar, then import into it.",
            )
            return@ScreenScaffold
        }
        LaunchedEffect(portfolio.id) { vm.loadAutoSync(portfolio.id) }

        val hasPreview = state.parsedTransactions.isNotEmpty()
        // One drop target shared by the empty-state drop zone and the preview table, so dropping a
        // new file replaces the current preview without first clearing it.
        val dropTarget = rememberCsvDropTarget(
            onDragHoverChange = { isDragHovered = it },
            onFilesDropped = { files -> vm.parseFiles(files, portfolio.id, portfolio.market) },
        )
        val portfolioLabel = "Into: ${portfolio.name} · ${portfolio.market.label}"

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxSize()) {
            if (hasPreview) {
                // Preview state: the table takes over the drop zone's space and stretches to fill the
                // screen; the broker sync card and section labels are collapsed to give it max height.
                PreviewHeader(
                    count = state.parsedTransactions.size,
                    detectionSummary = state.detectionSummary,
                    portfolioLabel = portfolioLabel,
                    selectedParser = state.selectedParser,
                    parsers = parsers,
                    onSelectParser = vm::selectParser,
                    onLoadAnother = vm::clearParsed,
                )

                (state.status as? ImportStatus.Error)?.let { ErrorBanner(it.message) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        // Highlight the table as a drop target so dragging a replacement file over it
                        // gives the same affordance the drop zone did in the empty state.
                        .then(
                            if (isDragHovered) {
                                Modifier
                                    .border(BorderStroke(2.dp, AppTheme.colors.accent), CardShape)
                                    .background(AppTheme.colors.accent.copy(alpha = 0.08f), CardShape)
                            } else {
                                Modifier
                            },
                        )
                        .dragAndDropTarget(
                            shouldStartDragAndDrop = { event ->
                                runCatching { event.dragData() is DragData.FilesList }.getOrDefault(false)
                            },
                            target = dropTarget,
                        ),
                ) {
                    DataTable(
                        columns = listOf("Symbol", "Date", "Action", "Price", "Shares", "Fees"),
                        rows = state.parsedTransactions,
                        cells = { tx ->
                            listOf(
                                tx.symbol,
                                tx.datetime.toString(),
                                tx.action.name,
                                "%.2f".format(tx.price),
                                "%.0f".format(tx.shares),
                                "%.2f".format(tx.fees),
                            )
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                AppPrimaryButton(
                    text = "Import ${state.parsedTransactions.size} rows",
                    // Bind the target portfolio at import time to the live selection, so switching
                    // portfolios after parsing saves into the one the "Into:" pill shows.
                    onClick = { vm.import(portfolio.id, onImportSuccess) },
                    enabled = state.status !is ImportStatus.Importing,
                    modifier = Modifier.align(Alignment.End),
                )
            } else {
                // Empty state: drop zone + browse, plus the optional broker-sync card.
                if (tradeZeroConfigured) SectionLabel("From CSV file")
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Pill(text = portfolioLabel)
                    ParserSelector(state.selectedParser, parsers, vm::selectParser)
                }

                DropZone(
                    isDragHovered = isDragHovered,
                    dropTarget = dropTarget,
                    onFilesPicked = { files -> vm.parseFiles(files, portfolio.id, portfolio.market) },
                )

                (state.status as? ImportStatus.Error)?.let { ErrorBanner(it.message) }

                state.detectionSummary?.let { summary ->
                    Text(
                        summary,
                        color = AppTheme.colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }

                if (tradeZeroConfigured) {
                    TradeZeroSection(
                        portfolioId = portfolio.id,
                        autoSyncOnStartup = state.autoSyncOnStartup,
                        onToggleAutoSync = { vm.setAutoSyncOnStartup(portfolio.id, it) },
                        syncService = tradeZeroSyncService,
                        onImportSuccess = onImportSuccess,
                    )
                }
            }
        }
    }
}

/** Compact summary + actions shown above the preview table once a file is loaded. */
@Composable
private fun PreviewHeader(
    count: Int,
    detectionSummary: String?,
    portfolioLabel: String,
    selectedParser: TransactionParser?,
    parsers: List<TransactionParser>,
    onSelectParser: (TransactionParser?) -> Unit,
    onLoadAnother: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                "$count transactions parsed",
                color = AppTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            detectionSummary?.let {
                Text(it, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Pill(text = portfolioLabel)
        ParserSelector(selectedParser, parsers, onSelectParser)
        AppSecondaryButton(text = "Load another file", onClick = onLoadAnother)
    }
}

/** Auto-detect / per-broker parser picker. Selecting a parser re-detects (clears the preview). */
@Composable
private fun ParserSelector(
    selected: TransactionParser?,
    parsers: List<TransactionParser>,
    onSelect: (TransactionParser?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppSecondaryButton(
            text = selected?.brokerName ?: "Auto-detect",
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Auto-detect") },
                onClick = { onSelect(null); expanded = false },
            )
            parsers.forEach { parser ->
                DropdownMenuItem(
                    text = { Text(parser.brokerName) },
                    onClick = { onSelect(parser); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DropZone(
    isDragHovered: Boolean,
    dropTarget: DragAndDropTarget,
    onFilesPicked: (List<ByteArray>) -> Unit,
) {
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
            .border(
                BorderStroke(
                    if (isDragHovered) 2.dp else 1.dp,
                    if (isDragHovered) AppTheme.colors.accent else AppTheme.colors.border,
                ),
                CardShape,
            )
            .background(
                if (isDragHovered) AppTheme.colors.accent.copy(alpha = 0.08f) else Color.Transparent,
                CardShape,
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    runCatching { event.dragData() is DragData.FilesList }.getOrDefault(false)
                },
                target = dropTarget,
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Text(
                if (isDragHovered) "Drop files here" else "Drag & drop CSV or XLSX files here",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (isDragHovered) AppTheme.colors.accent else AppTheme.colors.textPrimary,
            )
            Text(
                "Accepts broker CSV exports and eToro XLSX statements · drop several at once",
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.textMuted,
            )
            AppSecondaryButton(
                text = "Browse files…",
                onClick = {
                    scope.launch {
                        pickImportFiles().takeIf { it.isNotEmpty() }?.let(onFilesPicked)
                    }
                },
            )
        }
    }
}

/** The "From broker" Trade Zero sync card (only shown when Trade Zero credentials are configured). */
@Composable
private fun TradeZeroSection(
    portfolioId: Long,
    autoSyncOnStartup: Boolean,
    onToggleAutoSync: (Boolean) -> Unit,
    syncService: TradeZeroSyncService,
    onImportSuccess: () -> Unit,
) {
    var syncing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    SectionLabel("From broker")
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                "Trade Zero",
                color = AppTheme.colors.textPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Fetch all historical orders directly from your Trade Zero account.",
                color = AppTheme.colors.textMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                AppSecondaryButton(
                    text = if (syncing) "Syncing…" else "Sync TradeZero",
                    enabled = !syncing,
                    onClick = {
                        syncing = true
                        result = null
                        scope.launch {
                            when (val outcome = syncService.syncIncremental(portfolioId)) {
                                is TradeZeroSyncOutcome.Imported -> {
                                    result = "Imported ${outcome.inserted} new transaction(s)"
                                    if (outcome.inserted > 0) onImportSuccess()
                                }
                                TradeZeroSyncOutcome.InvalidCredentials ->
                                    result = "Invalid credentials — update them in Settings"
                                is TradeZeroSyncOutcome.NetworkError ->
                                    result = "Network error: ${outcome.message}"
                            }
                            syncing = false
                        }
                    },
                )
                result?.let {
                    Text(it, color = AppTheme.colors.textMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Switch(checked = autoSyncOnStartup, onCheckedChange = onToggleAutoSync)
                Text(
                    "Auto-sync this portfolio on startup",
                    color = AppTheme.colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/** Reads dropped CSV/XLSX file URIs into byte arrays, reporting drag-hover state. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun rememberCsvDropTarget(
    onDragHoverChange: (Boolean) -> Unit,
    onFilesDropped: (List<ByteArray>) -> Unit,
): DragAndDropTarget {
    val currentOnDragHoverChange by rememberUpdatedState(onDragHoverChange)
    val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)

    return remember {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                currentOnDragHoverChange(false)
                val data = event.dragData()
                if (data is DragData.FilesList) {
                    val files = data.readFiles()
                        .mapNotNull { runCatching { File(URI(it)).readBytes() }.getOrNull() }
                    if (files.isNotEmpty()) {
                        currentOnFilesDropped(files)
                        return true
                    }
                }
                return false
            }

            override fun onEntered(event: DragAndDropEvent) {
                currentOnDragHoverChange(true)
            }

            override fun onExited(event: DragAndDropEvent) {
                currentOnDragHoverChange(false)
            }

            override fun onEnded(event: DragAndDropEvent) {
                currentOnDragHoverChange(false)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = AppTheme.colors.textMuted, style = MaterialTheme.typography.labelSmall)
}
