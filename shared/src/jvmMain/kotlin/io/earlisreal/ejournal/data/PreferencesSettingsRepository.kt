package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate
import java.util.prefs.Preferences

class PreferencesSettingsRepository(
    private val prefs: Preferences = Preferences.userRoot().node("io/earlisreal/ejournal"),
) : SettingsRepository {

    override fun getThemeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(prefs.get(KEY_THEME, ThemeMode.SYSTEM.name)) }
            .getOrDefault(ThemeMode.SYSTEM)

    override fun setThemeMode(mode: ThemeMode) {
        prefs.put(KEY_THEME, mode.name)
    }

    override fun getFilterPrefs(): FilterPrefs? {
        val presetName = prefs.get(KEY_PRESET, "")
        if (presetName.isEmpty()) return null
        val preset = runCatching { DateRangePreset.valueOf(presetName) }.getOrNull() ?: return null
        val segment = runCatching { Segment.valueOf(prefs.get(KEY_SEGMENT, Segment.ALL.name)) }.getOrDefault(Segment.ALL)
        val portfolioId = prefs.getLong(KEY_PORTFOLIO, -1L).takeIf { it >= 0L }
        val from = prefs.get(KEY_FROM, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val to = prefs.get(KEY_TO, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return FilterPrefs(portfolioId, preset, from, to, segment)
    }

    override fun setFilterPrefs(prefs0: FilterPrefs) {
        prefs.putLong(KEY_PORTFOLIO, prefs0.portfolioId ?: -1L)
        prefs.put(KEY_PRESET, prefs0.preset.name)
        prefs.put(KEY_SEGMENT, prefs0.segment.name)
        prefs.put(KEY_FROM, prefs0.customFrom?.toString() ?: "")
        prefs.put(KEY_TO, prefs0.customTo?.toString() ?: "")
    }

    override fun getAutoSyncTradeZeroOnStartup(): Boolean = prefs.getBoolean(KEY_TZ_AUTO_SYNC, true)

    override fun setAutoSyncTradeZeroOnStartup(enabled: Boolean) {
        prefs.putBoolean(KEY_TZ_AUTO_SYNC, enabled)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_PORTFOLIO = "filter_portfolio_id"
        const val KEY_PRESET = "filter_preset"
        const val KEY_SEGMENT = "filter_segment"
        const val KEY_FROM = "filter_custom_from"
        const val KEY_TO = "filter_custom_to"
        const val KEY_TZ_AUTO_SYNC = "tradezero_auto_sync_startup"
    }
}
