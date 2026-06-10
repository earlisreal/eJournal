package io.earlisreal.ejournal.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.earlisreal.ejournal.data.repository.PortfolioRepository
import io.earlisreal.ejournal.data.repository.TransactionRepository
import io.earlisreal.ejournal.domain.model.Transaction
import io.earlisreal.ejournal.domain.parser.TransactionParser
import io.earlisreal.ejournal.ui.viewmodel.ImportStatus
import io.earlisreal.ejournal.ui.viewmodel.ImportViewModel
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ImportScreen(
    portfolioRepository: PortfolioRepository,
    transactionRepository: TransactionRepository,
    parsers: List<TransactionParser>,
    onImportSuccess: () -> Unit,
) {
    val vm = viewModel { ImportViewModel(transactionRepository, portfolioRepository, parsers) }
    val state by vm.state.collectAsState()

    var isDragHovered by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Import Transactions", style = MaterialTheme.typography.headlineMedium)

        // Selectors row
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.portfolios.isNotEmpty()) {
                var portfolioExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { portfolioExpanded = true }) {
                        Text(state.selectedPortfolio?.name ?: "Select Portfolio")
                    }
                    DropdownMenu(
                        expanded = portfolioExpanded,
                        onDismissRequest = { portfolioExpanded = false }
                    ) {
                        state.portfolios.forEach { portfolio ->
                            DropdownMenuItem(
                                text = { Text(portfolio.name) },
                                onClick = {
                                    vm.selectPortfolio(portfolio)
                                    portfolioExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                Text(
                    "No portfolios found. Create one in Portfolio Management first.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            var parserExpanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { parserExpanded = true }) {
                    Text(state.selectedParser?.brokerName ?: "Select Parser")
                }
                DropdownMenu(
                    expanded = parserExpanded,
                    onDismissRequest = { parserExpanded = false }
                ) {
                    parsers.forEach { parser ->
                        DropdownMenuItem(
                            text = { Text(parser.brokerName) },
                            onClick = {
                                vm.selectParser(parser)
                                parserExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Drop zone with drag & drop support
        DropZone(
            isDragHovered = isDragHovered,
            onDragHoverChange = { isDragHovered = it },
            onFilesDropped = { files -> vm.parseFiles(files) }
        )

        // Status message
        when (val status = state.status) {
            is ImportStatus.Error -> Text(
                status.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            else -> {}
        }

        // Preview table
        if (state.parsedTransactions.isNotEmpty()) {
            Text(
                "${state.parsedTransactions.size} transactions parsed",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            TransactionPreviewTable(
                transactions = state.parsedTransactions,
                modifier = Modifier.weight(1f)
            )

            Button(
                onClick = { vm.import(onImportSuccess) },
                enabled = state.status !is ImportStatus.Importing,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (state.status is ImportStatus.Importing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("Import ${state.parsedTransactions.size} rows")
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

@Composable
private fun TransactionPreviewTable(
    transactions: List<Transaction>,
    modifier: Modifier = Modifier,
) {
    val columns = listOf("Symbol", "Date", "Action", "Price", "Shares", "Fees")

    Column(
        modifier = modifier.border(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant,
            RoundedCornerShape(8.dp)
        )
    ) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            columns.forEach { col ->
                Text(
                    col,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider()

        LazyColumn {
            items(transactions) { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        tx.symbol,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        tx.datetime.toString(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        tx.action.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "%.2f".format(tx.price),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "%.0f".format(tx.shares),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "%.2f".format(tx.fees),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}
