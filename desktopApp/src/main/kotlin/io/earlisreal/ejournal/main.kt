package io.earlisreal.ejournal

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.demo.runCsvGenerator

fun main(args: Array<String>) {
    if (args.firstOrNull() == "generate-csv") {
        runCsvGenerator(args.drop(1).toTypedArray())
        return
    }
    if (args.firstOrNull() == "chart-test") {
        runChartTest(args.getOrElse(1) { "kandy" })
        return
    }

    application {
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
                credentialsRepository = deps.credentialsRepository,
                marketDataRepository = deps.marketDataRepository,
                parsers = deps.parsers,
                alpacaProvider = deps.alpacaProvider,
                marketDataService = deps.marketDataService,
            )
        }
    }
}
