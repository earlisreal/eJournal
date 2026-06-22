package io.earlisreal.ejournal

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.demo.runCsvGenerator
import io.earlisreal.ejournal.ui.chart.JavaFxChartBridge
import java.awt.Dimension

fun main(args: Array<String>) {
    StartupTrace.mark("main")
    FileLogging.init() // tee stdout/stderr to ~/.ejournal/logs so the packaged GUI app isn't silent

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
            // Shut down the JavaFX toolkit (kept alive by setImplicitExit(false)) before exiting,
            // so the non-daemon FX thread doesn't block JVM shutdown.
            onCloseRequest = {
                JavaFxChartBridge.shutdown()
                exitApplication()
            },
            state = windowState,
            title = "eJournal",
        ) {
            // Floor the window so the user can't drag it smaller than the layout supports.
            // minimumSize is raw device pixels (not dp), so it shrinks on HiDPI displays — tune if needed.
            LaunchedEffect(Unit) {
                window.minimumSize = Dimension(1100, 720)
            }
            App(
                portfolioRepository = deps.portfolioRepository,
                transactionRepository = deps.transactionRepository,
                settingsRepository = deps.settingsRepository,
                credentialsRepository = deps.credentialsRepository,
                marketDataRepository = deps.marketDataRepository,
                parsers = deps.parsers,
                alpacaProvider = deps.alpacaProvider,
                marketDataService = deps.marketDataService,
                tradeZeroClient = deps.tradeZeroClient,
                backgroundTaskTracker = deps.backgroundTaskTracker,
                tradeZeroSyncService = deps.tradeZeroSyncService,
                startupSyncCoordinator = deps.startupSyncCoordinator,
            )
        }
    }
}
