// shared/src/jvmMain/kotlin/io/earlisreal/ejournal/startup/AppStartup.kt
package io.earlisreal.ejournal.startup

import io.earlisreal.ejournal.AppDependencies
import io.earlisreal.ejournal.StartupTrace
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.shell.Destination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the first frame needs, built off the UI thread behind the splash. */
class ReadyApp(
    val deps: AppDependencies,
    val startDestination: Destination,
    val portfolios: List<Portfolio>,
)

/**
 * Builds the dependency graph and warms the first screen's framing (start destination + portfolio
 * list) on [Dispatchers.IO]. Per-screen data still loads via each screen's own effect after the
 * window appears — that brief load is acceptable.
 */
suspend fun buildReadyApp(): ReadyApp = withContext(Dispatchers.IO) {
    StartupTrace.mark("deps:start")
    val deps = AppDependencies()
    StartupTrace.mark("deps:done")
    val portfolios = deps.portfolioRepository.getAll()
    val savedPortfolioId = deps.settingsRepository.getFilterPrefs()?.portfolioId
    val startDestination = resolveStartDestination(
        portfolios = portfolios,
        savedPortfolioId = savedPortfolioId,
        countByPortfolio = { id -> deps.transactionRepository.countByPortfolio(id) },
    )
    StartupTrace.mark("warm:done")
    ReadyApp(deps, startDestination, portfolios)
}
