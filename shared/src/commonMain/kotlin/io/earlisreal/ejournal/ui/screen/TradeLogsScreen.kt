package io.earlisreal.ejournal.ui.screen

import androidx.compose.runtime.Composable
import io.earlisreal.ejournal.ui.components.EmptyState

@Composable
fun TradeLogsScreen() {
    EmptyState(
        title = "No closed positions yet",
        subtitle = "Import transactions to see your trade logs here.",
    )
}
