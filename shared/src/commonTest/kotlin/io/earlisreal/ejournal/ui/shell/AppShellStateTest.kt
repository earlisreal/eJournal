package io.earlisreal.ejournal.ui.shell

import androidx.compose.runtime.mutableStateOf
import io.earlisreal.ejournal.data.repository.PortfolioFilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppShellStateTest {

    @Test
    fun aNewPortfolioStartsWithCanonicalFilterDefaults() {
        assertEquals(
            PortfolioFilterPrefs(
                preset = DateRangePreset.ALL_TIME,
                segment = Segment.ALL,
                selectedTagIds = emptySet(),
                tagMatch = TagMatch.ANY,
            ),
            PortfolioFilterPrefs(),
        )
    }

    @Test
    fun portfolioChangeOnlyResetsAnalysisForADifferentPortfolio() {
        assertTrue(hasPortfolioChanged(1L, 2L))
        assertFalse(hasPortfolioChanged(1L, 1L))
        assertTrue(hasPortfolioChanged(null, 1L))
    }

    @Test
    fun activePortfolioAndFiltersAreReplacedTogether() {
        val first = ActivePortfolioFilters(portfolio(1L), PortfolioFilterPrefs(preset = DateRangePreset.THIS_MONTH))
        val second = ActivePortfolioFilters(portfolio(2L), PortfolioFilterPrefs(segment = Segment.SWING))
        val active = mutableStateOf(first)

        active.value = second

        assertEquals(second, active.value)
    }

    @Test
    fun portfolioReloadKeepsTheActivePortfolioOrFallsBackToTheFirst() {
        val active = portfolio(2L)
        val renamed = active.copy(name = "Renamed")
        val first = portfolio(1L)

        assertEquals(renamed, portfolioAfterReload(listOf(first, renamed), active.id))
        assertEquals(first, portfolioAfterReload(listOf(first), active.id))
        assertEquals(null, portfolioAfterReload(emptyList(), active.id))
    }

    @Test
    fun reloadingPortfolioMetadataPreservesFiltersOnlyForTheSamePortfolio() {
        val active = ActivePortfolioFilters(
            portfolio(2L),
            PortfolioFilterPrefs(preset = DateRangePreset.THIS_MONTH, segment = Segment.SWING),
        )
        val renamed = active.portfolio!!.copy(name = "Renamed")

        val refreshed = refreshPortfolioMetadata(active, renamed)

        assertEquals(renamed, refreshed?.portfolio)
        assertEquals(active.filters, refreshed?.filters)
        assertNull(refreshPortfolioMetadata(active, portfolio(1L)))
    }

    @Test
    fun deletingTagRemovesItFromTheActiveFilter() {
        assertEquals(setOf(2L), selectedTagsAfterTagDeletion(setOf(1L, 2L), 1L))
    }

    private fun portfolio(id: Long) = Portfolio(id, "Portfolio $id", Market.US_STOCKS, Broker.ALPACA, "credential")
}
