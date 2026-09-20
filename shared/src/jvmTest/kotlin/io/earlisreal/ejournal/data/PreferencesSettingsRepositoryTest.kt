package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.ui.theme.ThemeMode
import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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

    @Test
    fun persistsAndReadsBackEtapeDatabasePath() {
        val repo = PreferencesSettingsRepository(node)
        repo.setEtapeDbPath("C:/data/etape.db")

        assertEquals("C:/data/etape.db", PreferencesSettingsRepository(node).getEtapeDbPath())
    }

    @Test
    fun clearingEtapeDatabasePathRestoresUnsetState() {
        val repo = PreferencesSettingsRepository(node)
        repo.setEtapeDbPath("C:/data/etape.db")
        repo.setEtapeDbPath(null)

        assertNull(PreferencesSettingsRepository(node).getEtapeDbPath())
    }

    @Test
    fun networkPreferencesDefaultSafelyAndRoundTrip() {
        val repo = PreferencesSettingsRepository(node)
        assertEquals(false, repo.getOnlineMarketDataEnabled())
        assertEquals(true, repo.getAutomaticUpdateChecksEnabled())
        assertNull(repo.getNetworkDisclosureVersion())
        assertNull(repo.getLastUpdateCheckEpochMillis())

        repo.setOnlineMarketDataEnabled(true)
        repo.setAutomaticUpdateChecksEnabled(false)
        repo.setNetworkDisclosureVersion(1)
        repo.setLastUpdateCheckEpochMillis(123L)

        val restored = PreferencesSettingsRepository(node)
        assertEquals(true, restored.getOnlineMarketDataEnabled())
        assertEquals(false, restored.getAutomaticUpdateChecksEnabled())
        assertEquals(1, restored.getNetworkDisclosureVersion())
        assertEquals(123L, restored.getLastUpdateCheckEpochMillis())
    }
}
