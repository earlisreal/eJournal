package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate

/** Persisted filter selection. customFrom/customTo are only meaningful when preset == CUSTOM. */
data class FilterPrefs(
    val portfolioId: Long?,
    val preset: DateRangePreset,
    val customFrom: LocalDate?,
    val customTo: LocalDate?,
    val segment: Segment,
    val selectedTagIds: Set<Long> = emptySet(),
    val tagMatch: TagMatch = TagMatch.ANY,
)

interface SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getFilterPrefs(): FilterPrefs?   // null when nothing has been stored yet
    fun setFilterPrefs(prefs: FilterPrefs)
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
