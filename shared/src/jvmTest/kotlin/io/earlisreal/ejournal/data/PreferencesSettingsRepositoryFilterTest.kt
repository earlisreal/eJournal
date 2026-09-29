package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.PortfolioFilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
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
    fun selectedPortfolioRoundTripsAndCanBeCleared() {
        val repo = PreferencesSettingsRepository(node)
        repo.setSelectedPortfolioId(7L)
        assertEquals(7L, PreferencesSettingsRepository(node).getSelectedPortfolioId())
        repo.setSelectedPortfolioId(null)
        assertNull(PreferencesSettingsRepository(node).getSelectedPortfolioId())
    }

    @Test
    fun readsLegacySnapshotForMigration() {
        node.putLong("filter_portfolio_id", 7L)
        node.put("filter_preset", DateRangePreset.CUSTOM.name)
        node.put("filter_custom_from", "2024-01-05")
        node.put("filter_custom_to", "2024-02-06")
        node.put("filter_segment", Segment.DAY.name)
        node.put("filter_tag_ids_scoped", "3,5")
        node.put("filter_tag_match", TagMatch.ALL.name)

        val legacy = PreferencesSettingsRepository(node).getLegacyFilterPrefs()!!
        assertEquals(7L, legacy.portfolioId)
        assertEquals(DateRangePreset.CUSTOM, legacy.filters.preset)
        assertEquals(LocalDate(2024, 1, 5), legacy.filters.customFrom)
        assertEquals(LocalDate(2024, 2, 6), legacy.filters.customTo)
        assertEquals(Segment.DAY, legacy.filters.segment)
        assertEquals(setOf(3L, 5L), legacy.filters.selectedTagIds)
        assertEquals(TagMatch.ALL, legacy.filters.tagMatch)
    }

    @Test
    fun recoversInvalidLegacyFieldsIndependently() {
        node.putLong("filter_portfolio_id", 7L)
        node.put("filter_preset", DateRangePreset.CUSTOM.name)
        node.put("filter_custom_from", "not-a-date")
        node.put("filter_custom_to", "2024-02-06")
        node.put("filter_segment", "UNKNOWN")
        node.put("filter_tag_ids_scoped", "3,broken,-5,3")
        node.put("filter_tag_match", "UNKNOWN")

        val filters = PreferencesSettingsRepository(node).getLegacyFilterPrefs()!!.filters
        assertEquals(DateRangePreset.ALL_TIME, filters.preset)
        assertNull(filters.customFrom)
        assertNull(filters.customTo)
        assertEquals(Segment.ALL, filters.segment)
        assertEquals(setOf(3L), filters.selectedTagIds)
        assertEquals(TagMatch.ANY, filters.tagMatch)
    }

    @Test
    fun emptyPresetStillActsAsLegacySnapshotSentinel() {
        node.put("filter_preset", "")

        val filters = PreferencesSettingsRepository(node).getLegacyFilterPrefs()!!.filters

        assertEquals(PortfolioFilterPrefs(), filters)
    }

    @Test
    fun clearingLegacySnapshotPreservesSelectedPortfolio() {
        val repo = PreferencesSettingsRepository(node)
        repo.setSelectedPortfolioId(7L)
        node.put("filter_preset", DateRangePreset.ALL_TIME.name)
        node.put("filter_custom_from", "2024-01-01")
        node.put("filter_custom_to", "2024-01-02")
        node.put("filter_segment", Segment.DAY.name)
        node.put("filter_tag_ids_scoped", "3")
        node.put("filter_tag_ids", "11")
        node.put("filter_tag_match", TagMatch.ALL.name)

        repo.clearLegacyFilterPrefs()

        assertEquals(7L, repo.getSelectedPortfolioId())
        assertNull(repo.getLegacyFilterPrefs())
        assertEquals("", node.get("filter_tag_ids", ""))
    }
}
