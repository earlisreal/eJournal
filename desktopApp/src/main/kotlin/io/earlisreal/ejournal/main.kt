package io.earlisreal.ejournal

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    val deps = AppDependencies()
    Window(
        onCloseRequest = ::exitApplication,
        title = "eJournal",
    ) {
        App(
            portfolioRepository = deps.portfolioRepository,
            transactionRepository = deps.transactionRepository,
            parsers = deps.parsers
        )
    }
}
