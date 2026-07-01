package io.earlisreal.ejournal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState

@Composable
actual fun AppModalWindow(
    title: String,
    onDismiss: () -> Unit,
    widthDp: Int,
    heightDp: Int,
    content: @Composable () -> Unit,
) {
    // A real top-level dialog window: z-ordered above the parent window, so it never gets occluded.
    // CompositionLocals (e.g. the app theme) propagate from the parent composition into this window's
    // content.
    DialogWindow(
        onCloseRequest = onDismiss,
        state = rememberDialogState(size = DpSize(widthDp.dp, heightDp.dp)),
        title = title,
        resizable = true,
        content = { content() },
    )
}
