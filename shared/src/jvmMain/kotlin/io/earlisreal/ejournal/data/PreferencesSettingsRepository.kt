package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.SettingsRepository
import io.earlisreal.ejournal.ui.theme.ThemeMode
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

    private companion object {
        const val KEY_THEME = "theme_mode"
    }
}
