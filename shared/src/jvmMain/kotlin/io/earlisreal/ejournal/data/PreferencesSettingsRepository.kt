package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.LegacyFilterPrefs
import io.earlisreal.ejournal.data.repository.PortfolioFilterPrefs
import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.domain.analytics.DateRangePreset
import io.earlisreal.ejournal.domain.analytics.Segment
import io.earlisreal.ejournal.domain.analytics.TagMatch
import io.earlisreal.ejournal.ui.theme.ThemeMode
import kotlinx.datetime.LocalDate
import io.earlisreal.ejournal.data.repository.normalizePortfolioFilterPrefs
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

    override fun getSelectedPortfolioId(): Long? =
        prefs.getLong(KEY_PORTFOLIO, -1L).takeIf { it >= 0L }

    override fun setSelectedPortfolioId(portfolioId: Long?) {
        if (portfolioId == null) prefs.remove(KEY_PORTFOLIO)
        else prefs.putLong(KEY_PORTFOLIO, portfolioId)
    }

    override fun getLegacyFilterPrefs(): LegacyFilterPrefs? {
        val presetName = prefs.get(KEY_PRESET, null) ?: return null
        val preset = runCatching { DateRangePreset.valueOf(presetName) }.getOrDefault(DateRangePreset.ALL_TIME)
        val segment = runCatching { Segment.valueOf(prefs.get(KEY_SEGMENT, Segment.ALL.name)) }.getOrDefault(Segment.ALL)
        val portfolioId = prefs.getLong(KEY_PORTFOLIO, -1L).takeIf { it >= 0L }
        val from = prefs.get(KEY_FROM, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val to = prefs.get(KEY_TO, "").takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val tagIds = prefs.get(KEY_TAG_IDS, "").split(",").mapNotNull { it.toLongOrNull()?.takeIf { id -> id > 0L } }.toSet()
        val tagMatch = runCatching { TagMatch.valueOf(prefs.get(KEY_TAG_MATCH, TagMatch.ANY.name)) }.getOrDefault(TagMatch.ANY)
        return LegacyFilterPrefs(
            portfolioId,
            normalizePortfolioFilterPrefs(PortfolioFilterPrefs(preset, from, to, segment, tagIds, tagMatch)),
        )
    }

    override fun clearLegacyFilterPrefs() {
        listOf(KEY_PRESET, KEY_SEGMENT, KEY_FROM, KEY_TO, KEY_TAG_IDS, KEY_TAG_MATCH, KEY_OLD_TAG_IDS).forEach(prefs::remove)
    }

    override fun getEtapeDbPath(): String? = prefs.get(KEY_ETAPE_DB_PATH, "").takeIf { it.isNotBlank() }

    override fun setEtapeDbPath(path: String?) {
        if (path.isNullOrBlank()) prefs.remove(KEY_ETAPE_DB_PATH)
        else prefs.put(KEY_ETAPE_DB_PATH, path.trim())
    }

    override fun getOnlineMarketDataEnabled(): Boolean = prefs.getBoolean(KEY_ONLINE_MARKET_DATA, false)

    override fun setOnlineMarketDataEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_ONLINE_MARKET_DATA, enabled)
    }

    override fun getAutomaticUpdateChecksEnabled(): Boolean = prefs.getBoolean(KEY_UPDATE_CHECKS, true)

    override fun setAutomaticUpdateChecksEnabled(enabled: Boolean) {
        prefs.putBoolean(KEY_UPDATE_CHECKS, enabled)
    }

    override fun getNetworkDisclosureVersion(): Int? =
        prefs.get(KEY_NETWORK_DISCLOSURE_VERSION, "").toIntOrNull()

    override fun setNetworkDisclosureVersion(version: Int) {
        prefs.putInt(KEY_NETWORK_DISCLOSURE_VERSION, version)
    }

    override fun getLastUpdateCheckEpochMillis(): Long? =
        prefs.get(KEY_LAST_UPDATE_CHECK, "").toLongOrNull()

    override fun setLastUpdateCheckEpochMillis(epochMillis: Long) {
        prefs.putLong(KEY_LAST_UPDATE_CHECK, epochMillis)
    }

    private companion object {
        const val KEY_THEME = "theme_mode"
        const val KEY_PORTFOLIO = "filter_portfolio_id"
        const val KEY_PRESET = "filter_preset"
        const val KEY_SEGMENT = "filter_segment"
        const val KEY_FROM = "filter_custom_from"
        const val KEY_TO = "filter_custom_to"
        const val KEY_TAG_IDS = "filter_tag_ids_scoped"
        const val KEY_TAG_MATCH = "filter_tag_match"
        const val KEY_OLD_TAG_IDS = "filter_tag_ids"
        const val KEY_ETAPE_DB_PATH = "etape_db_path"
        const val KEY_ONLINE_MARKET_DATA = "online_market_data_enabled"
        const val KEY_UPDATE_CHECKS = "automatic_update_checks"
        const val KEY_NETWORK_DISCLOSURE_VERSION = "network_disclosure_version"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check_epoch_millis"
    }
}
