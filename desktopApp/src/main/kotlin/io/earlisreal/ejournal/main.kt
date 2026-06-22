package io.earlisreal.ejournal

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.demo.runCsvGenerator
import io.earlisreal.ejournal.startup.AsyncInitializer
import io.earlisreal.ejournal.startup.InitState
import io.earlisreal.ejournal.startup.buildReadyApp
import io.earlisreal.ejournal.ui.chart.JavaFxChartBridge
import io.earlisreal.ejournal.ui.startup.StartupErrorWindow
import java.awt.Dimension
import kotlinx.coroutines.launch

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
        // Build everything off the UI thread behind the native -splash; show the window only once ready.
        val initializer = remember { AsyncInitializer { buildReadyApp() } }
        val state by initializer.state.collectAsState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(Unit) { initializer.run() }

        when (val s = state) {
            // No visible window yet — the native -splash (Task 5) covers JVM boot + this build.
            is InitState.Loading -> Unit

            is InitState.Failed -> StartupErrorWindow(
                message = s.message,
                onRetry = { scope.launch { initializer.run() } },
                onQuit = ::exitApplication,
            )

            is InitState.Ready -> {
                val ready = s.value
                val windowState = rememberWindowState(size = DpSize(1360.dp, 880.dp))
                Window(
                    // Shut down the JavaFX toolkit before exiting so the non-daemon FX thread
                    // doesn't block JVM shutdown.
                    onCloseRequest = {
                        JavaFxChartBridge.shutdown()
                        exitApplication()
                    },
                    state = windowState,
                    title = "eJournal",
                ) {
                    LaunchedEffect(Unit) {
                        // minimumSize is raw device pixels (not dp); shrinks on HiDPI — tune if needed.
                        window.minimumSize = Dimension(1100, 720)
                        StartupTrace.mark("window-shown")
                        StartupTrace.logSummary()
                    }
                    App(
                        portfolioRepository = ready.deps.portfolioRepository,
                        transactionRepository = ready.deps.transactionRepository,
                        settingsRepository = ready.deps.settingsRepository,
                        credentialsRepository = ready.deps.credentialsRepository,
                        marketDataRepository = ready.deps.marketDataRepository,
                        parsers = ready.deps.parsers,
                        alpacaProvider = ready.deps.alpacaProvider,
                        marketDataService = ready.deps.marketDataService,
                        tradeZeroClient = ready.deps.tradeZeroClient,
                        backgroundTaskTracker = ready.deps.backgroundTaskTracker,
                        tradeZeroSyncService = ready.deps.tradeZeroSyncService,
                        startupSyncCoordinator = ready.deps.startupSyncCoordinator,
                        startDestination = ready.startDestination,
                        initialPortfolios = ready.portfolios,
                        closedPositions = ready.deps.closedPositionService,
                    )
                }
            }
        }
    }
}

