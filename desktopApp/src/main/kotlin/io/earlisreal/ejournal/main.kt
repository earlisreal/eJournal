package io.earlisreal.ejournal

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.demo.runCsvGenerator
import io.earlisreal.ejournal.startup.AsyncInitializer
import io.earlisreal.ejournal.startup.InitState
import io.earlisreal.ejournal.startup.buildReadyApp
import io.earlisreal.ejournal.ui.chart.ChartPreload
import io.earlisreal.ejournal.ui.chart.JcefRuntime
import io.earlisreal.ejournal.ui.startup.StartupErrorWindow
import java.awt.Dimension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun main(args: Array<String>) {
    StartupTrace.mark("main")
    FileLogging.init() // tee stdout/stderr to ~/.ejournal/logs so the packaged GUI app isn't silent

    // The Analysis chart is a heavyweight/windowed JCEF surface (SwingPanel with windowless rendering
    // off), which always paints above Compose's in-window layers — so any popup overlapping it (the
    // portfolio dropdown, tag pickers, the Manage-tags dialog) renders *behind* the chart. Rendering
    // Compose popups/dialogs as separate OS windows floats them above the heavyweight surface. Per the
    // JetBrains Swing-interop docs this must be set before any Compose code runs (i.e. before
    // application{}). See https://kotlinlang.org/docs/multiplatform/compose-desktop-swing-interoperability.html
    System.setProperty("compose.layers.type", "WINDOW")

    if (args.firstOrNull() == "generate-csv") {
        runCsvGenerator(args.drop(1).toTypedArray())
        return
    }
    application {
        // The eJournal window icon (title bar + Windows taskbar). Without this the JetBrains Runtime
        // logo is used. Resolved from the classpath root; the build copies desktopApp/icons/icon.png
        // there via processResources (see desktopApp/build.gradle.kts) so there's no duplicate PNG.
        val appIcon = painterResource("icon.png")
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
                icon = appIcon,
            )

            is InitState.Ready -> {
                val ready = s.value
                val windowState = rememberWindowState(size = DpSize(1360.dp, 880.dp))
                Window(
                    // Do NOT call JcefRuntime.dispose() (global CefApp.dispose()) here: it would run
                    // BEFORE exitApplication() tears down the composition — i.e. before the chart's
                    // DisposableEffect closes its CefBrowser — so CEF gets torn down while a browser is
                    // still alive, and the AppKit thread later dereferences freed CEF memory in
                    // +[NSEvent removeMonitor:] → SIGSEGV on close (exit 134). Per-browser teardown
                    // (browser.close + client.dispose) stays in JcefChartBridge.dispose(); the global
                    // CefApp is reclaimed by process exit / java-cef's own shutdown handling.
                    onCloseRequest = ::exitApplication,
                    state = windowState,
                    title = "eJournal",
                    icon = appIcon,
                ) {
                    LaunchedEffect(Unit) {
                        // minimumSize is raw device pixels (not dp); shrinks on HiDPI — tune if needed.
                        window.minimumSize = Dimension(1100, 720)
                        StartupTrace.mark("window-shown")
                        StartupTrace.logSummary()
                    }
                    LaunchedEffect(Unit) {
                        delay(1500) // let the first frame paint and settle before warming the chart
                        withContext(Dispatchers.IO) { JcefRuntime.warmUp() } // off-EDT: 100MB CEF dl on first run
                        ChartPreload.warm() // bridge ctor on EDT is now fast (warmUp already done)
                    }
                    App(
                        portfolioRepository = ready.deps.portfolioRepository,
                        transactionRepository = ready.deps.transactionRepository,
                        settingsRepository = ready.deps.settingsRepository,
                        portfolioSettings = ready.deps.portfolioSettingsRepository,
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
                        positionTags = ready.deps.positionTagService,
                        tagRepository = ready.deps.tagRepository,
                    )
                }
            }
        }
    }
}
