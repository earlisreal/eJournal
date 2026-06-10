package io.earlisreal.ejournal.ui.shell

const val SIDEBAR_COLLAPSE_BREAKPOINT_DP = 900

enum class SidebarState { EXPANDED, COLLAPSED }

fun resolveSidebarState(windowWidthDp: Int, userExpanded: Boolean): SidebarState =
    when {
        windowWidthDp < SIDEBAR_COLLAPSE_BREAKPOINT_DP -> SidebarState.COLLAPSED
        userExpanded -> SidebarState.EXPANDED
        else -> SidebarState.COLLAPSED
    }
