package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.FilterPrefs
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
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
        val tagIds = prefs.get(KEY_TAG_IDS, "").split(",").mapNotNull { it.toLongOrNull() }.toSet()
        val tagMatch = runCatching { TagMatch.valueOf(prefs.get(KEY_TAG_MATCH, TagMatch.ANY.name)) }.getOrDefault(TagMatch.ANY)
        return FilterPrefs(portfolioId, preset, from, to, segment, tagIds, tagMatch)
    }

    override fun setFilterPrefs(prefs0: FilterPrefs) {
        prefs.putLong(KEY_PORTFOLIO, prefs0.portfolioId ?: -1L)
        prefs.put(KEY_PRESET, prefs0.preset.name)
        prefs.put(KEY_SEGMENT, prefs0.segment.name)
        prefs.put(KEY_FROM, prefs0.customFrom?.toString() ?: "")
        prefs.put(KEY_TO, prefs0.customTo?.toString() ?: "")
        prefs.put(KEY_TAG_IDS, prefs0.selectedTagIds.joinToString(","))
        prefs.put(KEY_TAG_MATCH, prefs0.tagMatch.name)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_PORTFOLIO = "filter_portfolio_id"
        const val KEY_PRESET = "filter_preset"
        const val KEY_SEGMENT = "filter_segment"
        const val KEY_FROM = "filter_custom_from"
        const val KEY_TO = "filter_custom_to"
        const val KEY_TAG_IDS = "filter_tag_ids"
        const val KEY_TAG_MATCH = "filter_tag_match"
    }
}
