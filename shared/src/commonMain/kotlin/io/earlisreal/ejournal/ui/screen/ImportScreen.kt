package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.ui.components.AppPrimaryButton
import io.earlisreal.ejournal.ui.components.AppSecondaryButton
import io.earlisreal.ejournal.ui.components.DataTable
import io.earlisreal.ejournal.ui.components.EmptyState
import io.earlisreal.ejournal.ui.components.ErrorBanner
import io.earlisreal.ejournal.ui.components.Pill
import io.earlisreal.ejournal.ui.components.ScreenScaffold
import io.earlisreal.ejournal.ui.shell.FilterState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing
import io.earlisreal.ejournal.ui.viewmodel.ImportStatus
import io.earlisreal.ejournal.ui.viewmodel.ImportViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImportScreen(
    transactionRepository: TransactionRepository,
    parsers: List<TransactionParser>,
    filter: FilterState,
    onImportSuccess: () -> Unit,
) {
    val vm = viewModel { ImportViewModel(transactionRepository, parsers) }
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
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.lg), modifier = Modifier.fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Pill(text = "Into: ${portfolio.name} · ${portfolio.market.label}")

                var parserExpanded by remember { mutableStateOf(false) }
                Box {
                    AppSecondaryButton(
                        text = state.selectedParser?.brokerName ?: "Select Parser",
                        onClick = { parserExpanded = true },
                    )
                    DropdownMenu(expanded = parserExpanded, onDismissRequest = { parserExpanded = false }) {
                        parsers.forEach { parser ->
                            DropdownMenuItem(
                                text = { Text(parser.brokerName) },
                                onClick = { vm.selectParser(parser); parserExpanded = false },
                            )
                        }
                    }
                }
            }

            DropZone(
                isDragHovered = isDragHovered,
                onDragHoverChange = { isDragHovered = it },
                onFilesDropped = { files -> vm.parseFiles(files, portfolio.id) },
            )

            when (val status = state.status) {
                is ImportStatus.Error -> ErrorBanner(status.message)
                else -> {}
            }

            if (state.parsedTransactions.isNotEmpty()) {
                Text(
                    "${state.parsedTransactions.size} transactions parsed",
                    color = AppTheme.colors.textMuted,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
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
                    modifier = Modifier.weight(1f),
                )
                AppPrimaryButton(
                    text = "Import ${state.parsedTransactions.size} rows",
                    onClick = { vm.import(onImportSuccess) },
                    enabled = state.status !is ImportStatus.Importing,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DropZone(
    isDragHovered: Boolean,
    onDragHoverChange: (Boolean) -> Unit,
    onFilesDropped: (List<ByteArray>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val currentOnDragHoverChange by rememberUpdatedState(onDragHoverChange)
    val currentOnFilesDropped by rememberUpdatedState(onFilesDropped)

    val dragTarget = remember {
        object : androidx.compose.ui.draganddrop.DragAndDropTarget {
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .border(
                BorderStroke(
                    2.dp,
                    if (isDragHovered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
                ),
                RoundedCornerShape(12.dp)
            )
            .background(
                if (isDragHovered) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .dragAndDropTarget(
                shouldStartDragAndDrop = { event ->
                    runCatching { event.dragData() is DragData.FilesList }.getOrDefault(false)
                },
                target = dragTarget
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (isDragHovered) "Drop files here" else "Drag & drop CSV files here",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = {
                scope.launch(Dispatchers.IO) {
                    val dialog = FileDialog(null as Frame?, "Select CSV files", FileDialog.LOAD).apply {
                        isMultipleMode = true
                        file = "*.csv"
                        isVisible = true
                    }
                    val files = dialog.files?.map { it.readBytes() } ?: emptyList()
                    if (files.isNotEmpty()) currentOnFilesDropped(files)
                }
            }) {
                Text("Browse files…")
            }
        }
    }
}
