package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate

/** Persisted filter selection. customFrom/customTo are only meaningful when preset == CUSTOM. */
data class FilterPrefs(
    val portfolioId: Long?,
    val preset: DateRangePreset,
    val customFrom: LocalDate?,
    val customTo: LocalDate?,
    val segment: Segment,
)

interface SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun getFilterPrefs(): FilterPrefs?   // null when nothing has been stored yet
    fun setFilterPrefs(prefs: FilterPrefs)
    /** Whether TradeZero orders are pulled automatically on app startup. Defaults to true. */
    fun getAutoSyncTradeZeroOnStartup(): Boolean
    fun setAutoSyncTradeZeroOnStartup(enabled: Boolean)
}
