package io.earlisreal.ejournal.startup

import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.ui.shell.Destination
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StartDestinationTest {

    private val portfolios = listOf(Portfolio(1L, "Main", Market.US_STOCKS))

    @Test
    fun dashboardWhenSelectedPortfolioHasTransactions() = runTest {
        val dest = resolveStartDestination(portfolios, savedPortfolioId = 1L) { 3L }
        assertEquals(Destination.DASHBOARD, dest)
    }

    @Test
    fun defaultWhenSelectedPortfolioHasNoTransactions() = runTest {
        val dest = resolveStartDestination(portfolios, savedPortfolioId = 1L) { 0L }
        assertEquals(Destination.DEFAULT, dest)
    }

    @Test
    fun defaultWhenNoPortfolios() = runTest {
        val dest = resolveStartDestination(emptyList(), savedPortfolioId = null) { 9L }
        assertEquals(Destination.DEFAULT, dest)
    }

    @Test
    fun fallsBackToFirstPortfolioWhenNoSavedId() = runTest {
        var askedId = -1L
        val dest = resolveStartDestination(portfolios, savedPortfolioId = null) { id -> askedId = id; 5L }
        assertEquals(1L, askedId)
        assertEquals(Destination.DASHBOARD, dest)
    }
}
