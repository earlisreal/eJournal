package io.earlisreal.ejournal.ui.components

import androidx.compose.runtime.Composable

/**
 * A modal window rendered by the platform. On desktop this is a real OS dialog window, so it reliably
 * floats above the main app window without the auto-sizing fragility of a `Popup` used as a modal.
 * [content] fills the window; the window is resizable and closes via [onDismiss].
 */
@Composable
expect fun AppModalWindow(
    title: String,
    onDismiss: () -> Unit,
    widthDp: Int = 500,
    heightDp: Int = 640,
    content: @Composable () -> Unit,
)
