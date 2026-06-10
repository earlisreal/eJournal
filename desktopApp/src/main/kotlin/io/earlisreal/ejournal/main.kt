package io.earlisreal.ejournal

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    val deps = AppDependencies()
    val windowState = rememberWindowState(size = DpSize(1360.dp, 880.dp))
    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "eJournal",
    ) {
        App(
            portfolioRepository = deps.portfolioRepository,
            transactionRepository = deps.transactionRepository,
            settingsRepository = deps.settingsRepository,
            parsers = deps.parsers
        )
    }
}
