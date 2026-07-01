package io.earlisreal.ejournal.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DestinationTest {

    @Test
    fun destinationsAreInSidebarOrder() {
        assertEquals(
            listOf("Dashboard", "Trade Logs", "Import", "Calendar", "Reports", "Analysis", "Settings"),
            Destination.entries.map { it.label }
        )
    }

    @Test
    fun allDestinationsAreEnabled() {
        assertEquals(
            Destination.entries.toSet(),
            Destination.entries.filter { it.enabled }.toSet()
        )
    }

    @Test
    fun onlySettingsIsPinnedToTheSidebarBottom() {
        assertEquals(listOf(Destination.SETTINGS), Destination.entries.filter { it.pinnedBottom })
    }

    @Test
    fun defaultDestinationIsImport() {
        assertEquals(Destination.IMPORT, Destination.DEFAULT)
        assertTrue(Destination.DEFAULT.enabled)
    }
}
