package io.earlisreal.ejournal.ui.theme

enum class ThemeMode { SYSTEM, LIGHT, DARK }

fun resolveDarkMode(mode: ThemeMode, systemInDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}
