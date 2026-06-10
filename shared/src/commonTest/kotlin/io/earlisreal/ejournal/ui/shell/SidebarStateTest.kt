package io.earlisreal.ejournal.ui.shell

import kotlin.test.Test
import kotlin.test.assertEquals

class SidebarStateTest {

    @Test
    fun belowBreakpointForcesCollapsedEvenWhenUserPrefersExpanded() {
        assertEquals(
            SidebarState.COLLAPSED,
            resolveSidebarState(windowWidthDp = SIDEBAR_COLLAPSE_BREAKPOINT_DP - 1, userExpanded = true)
        )
    }

    @Test
    fun atOrAboveBreakpointHonorsUserExpandedPreference() {
        assertEquals(
            SidebarState.EXPANDED,
            resolveSidebarState(windowWidthDp = SIDEBAR_COLLAPSE_BREAKPOINT_DP, userExpanded = true)
        )
        assertEquals(
            SidebarState.COLLAPSED,
            resolveSidebarState(windowWidthDp = SIDEBAR_COLLAPSE_BREAKPOINT_DP, userExpanded = false)
        )
    }

    @Test
    fun wideWindowWithCollapsePreferenceStaysCollapsed() {
        assertEquals(
            SidebarState.COLLAPSED,
            resolveSidebarState(windowWidthDp = 1600, userExpanded = false)
        )
    }
}
