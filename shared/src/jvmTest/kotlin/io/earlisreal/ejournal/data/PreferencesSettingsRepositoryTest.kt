package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.ui.theme.ThemeMode
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PreferencesSettingsRepositoryTest {

    private val node: Preferences =
        Preferences.userRoot().node("io/earlisreal/ejournal/test-${System.nanoTime()}")

    @AfterTest
    fun cleanup() {
        node.removeNode()
        node.flush()
    }

    @Test
    fun defaultsToSystemWhenUnset() {
        val repo = PreferencesSettingsRepository(node)
        assertEquals(ThemeMode.SYSTEM, repo.getThemeMode())
    }

    @Test
    fun persistsAndReadsBackThemeMode() {
        val repo = PreferencesSettingsRepository(node)
        repo.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, PreferencesSettingsRepository(node).getThemeMode())
    }

    @Test
    fun fallsBackToSystemOnCorruptValue() {
        node.put("theme_mode", "not-a-real-mode")
        assertEquals(ThemeMode.SYSTEM, PreferencesSettingsRepository(node).getThemeMode())
    }
}
