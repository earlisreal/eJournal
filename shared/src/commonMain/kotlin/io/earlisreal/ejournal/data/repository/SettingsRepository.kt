package io.earlisreal.ejournal.data.repository

import io.earlisreal.ejournal.ui.theme.ThemeMode

interface SettingsRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
}
