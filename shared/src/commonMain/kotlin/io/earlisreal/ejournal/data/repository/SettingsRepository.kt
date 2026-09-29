package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate

/** One Portfolio's shared filter snapshot. Custom bounds are meaningful only for CUSTOM. */
data class PortfolioFilterPrefs(
    val preset: DateRangePreset = DateRangePreset.ALL_TIME,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val segment: Segment = Segment.ALL,
    val selectedTagIds: Set<Long> = emptySet(),
    val tagMatch: TagMatch = TagMatch.ANY,
)

/** Legacy machine-wide filter state, read only while migrating to Portfolio settings. */
data class LegacyFilterPrefs(
    val portfolioId: Long?,
    val filters: PortfolioFilterPrefs,
)

interface SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getSelectedPortfolioId(): Long?
    fun setSelectedPortfolioId(portfolioId: Long?)
    fun getLegacyFilterPrefs(): LegacyFilterPrefs?
    fun clearLegacyFilterPrefs()
    fun getEtapeDbPath(): String? = null
    fun setEtapeDbPath(path: String?) = Unit

    /** Permits automatic Yahoo/Alpaca requests. Missing preferences are treated as disabled. */
    fun getOnlineMarketDataEnabled(): Boolean = false
    fun setOnlineMarketDataEnabled(enabled: Boolean) = Unit

    /** GitHub release checks are independent and enabled by default. */
    fun getAutomaticUpdateChecksEnabled(): Boolean = true
    fun setAutomaticUpdateChecksEnabled(enabled: Boolean) = Unit

    fun getNetworkDisclosureVersion(): Int? = null
    fun setNetworkDisclosureVersion(version: Int) = Unit
    fun getLastUpdateCheckEpochMillis(): Long? = null
    fun setLastUpdateCheckEpochMillis(epochMillis: Long) = Unit
}
