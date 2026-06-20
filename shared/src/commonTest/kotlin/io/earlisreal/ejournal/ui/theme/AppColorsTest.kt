package io.earlisreal.ejournal.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AppColorsTest {

    @Test
    fun lightAccentIsAmberGold() {
        assertEquals(Color(0xFFD99125), lightAppColors.accent)
    }

    @Test
    fun profitIsGreenAndLossIsRedInBothThemes() {
        assertEquals(Color(0xFF16A34A), lightAppColors.profit)
        assertEquals(Color(0xFFDC2626), lightAppColors.loss)
        assertNotEquals(lightAppColors.profit, lightAppColors.loss)
        assertNotEquals(darkAppColors.profit, darkAppColors.loss)
    }

    @Test
    fun darkBackgroundDiffersFromLightBackground() {
        assertNotEquals(lightAppColors.background, darkAppColors.background)
    }

    @Test
    fun lightContentIsWhiteAndSidebarIsOffWhite() {
        assertEquals(Color(0xFFFFFFFF), lightAppColors.contentBackground)
        assertEquals(Color(0xFFF4F5F9), lightAppColors.sidebarBackground)
        assertNotEquals(lightAppColors.contentBackground, lightAppColors.sidebarBackground)
    }

    @Test
    fun darkContentAndSidebarPreserveExistingSurfaces() {
        assertEquals(darkAppColors.background, darkAppColors.contentBackground)
        assertEquals(darkAppColors.surface, darkAppColors.sidebarBackground)
    }
}
