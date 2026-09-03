package io.earlisreal.ejournal.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppShellStateTest {

    @Test
    fun changingPortfolioClearsTagsButReselectingSamePortfolioKeepsThem() {
        val selected = setOf(1L, 2L)

        assertEquals(emptySet(), selectedTagsAfterPortfolioChange(selected, 1L, 2L))
        assertEquals(selected, selectedTagsAfterPortfolioChange(selected, 1L, 1L))
    }

    @Test
    fun portfolioChangeOnlyResetsAnalysisForADifferentPortfolio() {
        assertTrue(hasPortfolioChanged(1L, 2L))
        assertFalse(hasPortfolioChanged(1L, 1L))
        assertTrue(hasPortfolioChanged(null, 1L))
    }

    @Test
    fun deletingTagRemovesItFromTheActiveFilter() {
        assertEquals(setOf(2L), selectedTagsAfterTagDeletion(setOf(1L, 2L), 1L))
    }
}
