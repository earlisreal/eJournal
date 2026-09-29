package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.LegacyFilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioSettingsRepository
import io.earlisreal.ejournal.data.repository.TagRepository
import io.earlisreal.ejournal.data.repository.loadInitialPortfolioFilterState
import io.earlisreal.ejournal.data.repository.loadPortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.putPortfolioFilterPrefs
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.domain.model.Broker
import io.earlisreal.ejournal.domain.model.Market
import io.earlisreal.ejournal.domain.model.Portfolio
import io.earlisreal.ejournal.domain.model.Tag
import io.earlisreal.ejournal.testutil.FakeSettingsRepository
import io.earlisreal.ejournal.ui.shell.ActivePortfolioFilters
import io.earlisreal.ejournal.ui.shell.restoreActivePortfolioFilters
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PortfolioFilterSettingsMigrationTest {

    private val portfolio = Portfolio(7L, "US", Market.US_STOCKS, Broker.ALPACA, "credential")

    @Test
    fun migratesLegacySnapshotOnceAndDropsTagsThatAreNotOwned() = runTest {
        val legacy = PortfolioFilterPrefs(
            preset = DateRangePreset.THIS_MONTH,
            segment = Segment.DAY,
            selectedTagIds = setOf(3L, 4L),
            tagMatch = TagMatch.ALL,
        )
        val settings = FakeSettingsRepository(7L, LegacyFilterPrefs(7L, legacy))
        val portfolioSettings = InMemoryPortfolioSettings()
        val tags = TestTags(listOf(Tag(3L, 7L, "Owned", "#123456")))

        val initial = loadInitialPortfolioFilterState(listOf(portfolio), settings, portfolioSettings, tags)
        assertEquals(portfolio, initial.portfolio)
        assertEquals(legacy.copy(selectedTagIds = setOf(3L)), initial.filters)
        assertNull(settings.legacyFilterPrefsValue)
        assertEquals(initial.filters, portfolioSettings.loadPortfolioFilterPrefs(7L, tags))

        val newer = PortfolioFilterPrefs(preset = DateRangePreset.LAST_YEAR)
        portfolioSettings.putPortfolioFilterPrefs(7L, newer)
        val restarted = loadInitialPortfolioFilterState(listOf(portfolio), settings, portfolioSettings, tags)
        assertEquals(newer, restarted.filters)
    }

    @Test
    fun fallbackPortfolioUsesDefaultsAndReplacesMissingSelectedId() = runTest {
        val settings = FakeSettingsRepository(
            selectedPortfolioId = 99L,
            legacyFilterPrefs = LegacyFilterPrefs(99L, PortfolioFilterPrefs(preset = DateRangePreset.THIS_WEEK)),
        )
        val initial = loadInitialPortfolioFilterState(
            listOf(portfolio), settings, InMemoryPortfolioSettings(), TestTags(emptyList()),
        )

        assertEquals(portfolio, initial.portfolio)
        assertEquals(PortfolioFilterPrefs(), initial.filters)
        assertEquals(7L, settings.selectedPortfolioIdValue)
        assertNull(settings.legacyFilterPrefsValue)
    }

    @Test
    fun existingPortfolioSnapshotWinsOverLegacyStateAndStaleTagsAreRemoved() = runTest {
        val current = PortfolioFilterPrefs(preset = DateRangePreset.LAST_YEAR, selectedTagIds = setOf(3L, 4L))
        val settings = FakeSettingsRepository(
            selectedPortfolioId = 7L,
            legacyFilterPrefs = LegacyFilterPrefs(7L, PortfolioFilterPrefs(preset = DateRangePreset.THIS_WEEK)),
        )
        val portfolioSettings = InMemoryPortfolioSettings().apply { putPortfolioFilterPrefs(7L, current) }
        val tags = TestTags(listOf(Tag(3L, 7L, "Owned", "#123456")))

        val initial = loadInitialPortfolioFilterState(listOf(portfolio), settings, portfolioSettings, tags)

        assertEquals(DateRangePreset.LAST_YEAR, initial.filters.preset)
        assertEquals(setOf(3L), initial.filters.selectedTagIds)
        assertEquals(initial.filters, portfolioSettings.loadPortfolioFilterPrefs(7L, tags))
        assertNull(settings.legacyFilterPrefsValue)
    }

    @Test
    fun switchingBetweenPortfoliosRestoresEachSavedSnapshot() = runTest {
        val second = Portfolio(8L, "PH", Market.PH_STOCKS, Broker.ALPACA, "credential")
        val settingsRepository = FakeSettingsRepository(selectedPortfolioId = portfolio.id)
        val settings = InMemoryPortfolioSettings()
        val tags = TestTags(emptyList())
        val firstFilters = PortfolioFilterPrefs(preset = DateRangePreset.THIS_MONTH, segment = Segment.DAY)
        val secondFilters = PortfolioFilterPrefs(preset = DateRangePreset.LAST_YEAR, segment = Segment.SWING)
        settings.putPortfolioFilterPrefs(portfolio.id, firstFilters)
        settings.putPortfolioFilterPrefs(second.id, secondFilters)

        assertEquals(
            ActivePortfolioFilters(second, secondFilters),
            restoreActivePortfolioFilters(second, settings, settingsRepository, tags),
        )
        assertEquals(8L, settingsRepository.selectedPortfolioIdValue)
        assertEquals(
            ActivePortfolioFilters(portfolio, firstFilters),
            restoreActivePortfolioFilters(portfolio, settings, settingsRepository, tags),
        )
        assertEquals(7L, settingsRepository.selectedPortfolioIdValue)
    }

    @Test
    fun failedLegacyWriteKeepsTheSnapshotForRetry() = runTest {
        val legacy = LegacyFilterPrefs(7L, PortfolioFilterPrefs(preset = DateRangePreset.THIS_WEEK))
        val settings = FakeSettingsRepository(selectedPortfolioId = 99L, legacyFilterPrefs = legacy)
        val portfolioSettings = InMemoryPortfolioSettings(failWrites = true)
        val tags = TestTags(emptyList())

        assertFailsWith<IllegalStateException> {
            loadInitialPortfolioFilterState(listOf(portfolio), settings, portfolioSettings, tags)
        }
        assertEquals(legacy, settings.legacyFilterPrefsValue)
        assertEquals(99L, settings.selectedPortfolioIdValue)

        portfolioSettings.failWrites = false
        val initial = loadInitialPortfolioFilterState(listOf(portfolio), settings, portfolioSettings, tags)
        assertEquals(DateRangePreset.THIS_WEEK, initial.filters.preset)
        assertNull(settings.legacyFilterPrefsValue)
        assertEquals(7L, settings.selectedPortfolioIdValue)
    }

    private class InMemoryPortfolioSettings(var failWrites: Boolean = false) : PortfolioSettingsRepository {
        private val values = mutableMapOf<Pair<Long, String>, String>()

        override suspend fun getString(portfolioId: Long, key: String): String? = values[portfolioId to key]

        override suspend fun putString(portfolioId: Long, key: String, value: String) {
            if (failWrites) error("simulated write failure")
            values[portfolioId to key] = value
        }

        override suspend fun getBoolean(portfolioId: Long, key: String, default: Boolean): Boolean =
            values[portfolioId to key]?.toBooleanStrictOrNull() ?: default

        override suspend fun putBoolean(portfolioId: Long, key: String, value: Boolean) {
            putString(portfolioId, key, value.toString())
        }

        override suspend fun clearNamespace(portfolioId: Long, namespace: String) {
            values.keys.removeAll { it.first == portfolioId && it.second.startsWith(namespace) }
        }

        override suspend fun clear(portfolioId: Long) {
            values.keys.removeAll { it.first == portfolioId }
        }
    }

    private class TestTags(private val tags: List<Tag>) : TagRepository {
        override suspend fun getAll(portfolioId: Long): List<Tag> = tags.filter { it.portfolioId == portfolioId }
        override suspend fun create(portfolioId: Long, name: String, color: String): Long = error("unused")
        override suspend fun update(portfolioId: Long, id: Long, name: String, color: String) = error("unused")
        override suspend fun delete(portfolioId: Long, id: Long) = error("unused")
        override suspend fun getTagsForOpeningTxIds(portfolioId: Long, openingTxIds: List<Long>): Map<Long, List<Tag>> = error("unused")
        override suspend fun addTag(portfolioId: Long, openingTxId: Long, tagId: Long) = error("unused")
        override suspend fun removeTag(portfolioId: Long, openingTxId: Long, tagId: Long) = error("unused")
    }
}
