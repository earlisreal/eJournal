package io.earlisreal.ejournal.ui.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun systemModeFollowsSystemDarkValue() {
        assertTrue(resolveDarkMode(ThemeMode.SYSTEM, systemInDark = true))
        assertFalse(resolveDarkMode(ThemeMode.SYSTEM, systemInDark = false))
    }

    @Test
    fun lightModeIsNeverDark() {
        assertFalse(resolveDarkMode(ThemeMode.LIGHT, systemInDark = true))
        assertFalse(resolveDarkMode(ThemeMode.LIGHT, systemInDark = false))
    }

    @Test
    fun darkModeIsAlwaysDark() {
        assertTrue(resolveDarkMode(ThemeMode.DARK, systemInDark = true))
        assertTrue(resolveDarkMode(ThemeMode.DARK, systemInDark = false))
    }
}
