package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import kotlinx.datetime.LocalDate
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreferencesSettingsRepositoryFilterTest {

    private val node: Preferences =
        Preferences.userRoot().node("io/earlisreal/ejournal/ftest-${System.nanoTime()}")

    @AfterTest
    fun cleanup() {
        node.removeNode(); node.flush()
    }

    @Test
    fun nullWhenNothingStored() {
        assertNull(PreferencesSettingsRepository(node).getFilterPrefs())
    }

    @Test
    fun roundTripsPresetSegmentAndPortfolio() {
        val repo = PreferencesSettingsRepository(node)
        repo.setFilterPrefs(FilterPrefs(portfolioId = 7L, preset = DateRangePreset.THIS_MONTH, customFrom = null, customTo = null, segment = Segment.DAY))
        val read = PreferencesSettingsRepository(node).getFilterPrefs()!!
        assertEquals(7L, read.portfolioId)
        assertEquals(DateRangePreset.THIS_MONTH, read.preset)
        assertEquals(Segment.DAY, read.segment)
    }

    @Test
    fun roundTripsCustomDates() {
        val repo = PreferencesSettingsRepository(node)
        repo.setFilterPrefs(FilterPrefs(portfolioId = null, preset = DateRangePreset.CUSTOM, customFrom = LocalDate(2024, 1, 5), customTo = LocalDate(2024, 2, 6), segment = Segment.ALL))
        val read = PreferencesSettingsRepository(node).getFilterPrefs()!!
        assertNull(read.portfolioId)
        assertEquals(DateRangePreset.CUSTOM, read.preset)
        assertEquals(LocalDate(2024, 1, 5), read.customFrom)
        assertEquals(LocalDate(2024, 2, 6), read.customTo)
    }
}
