package io.earlisreal.ejournal.startup

import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.shell.Destination

/**
 * Picks the screen to open on first launch: Dashboard when the selected portfolio (the saved one,
 * else the first) has any transactions; otherwise the default (Import). Mirrors the logic formerly
 * inlined in AppShell so it can be resolved behind the splash before the window is shown.
 */
suspend fun resolveStartDestination(
    portfolios: List<Portfolio>,
    savedPortfolioId: Long?,
    countByPortfolio: suspend (Long) -> Long,
): Destination {
    val selected = savedPortfolioId?.let { id -> portfolios.firstOrNull { it.id == id } }
        ?: portfolios.firstOrNull()
    return if (selected != null && countByPortfolio(selected.id) > 0L) Destination.DASHBOARD
    else Destination.DEFAULT
}
