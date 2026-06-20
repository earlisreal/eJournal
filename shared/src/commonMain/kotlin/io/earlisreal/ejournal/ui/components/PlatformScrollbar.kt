package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Draws a platform-appropriate vertical scrollbar for a [LazyListState]. On desktop this is the
 * native Compose scrollbar; other targets render nothing. Overlay it on top of the scrollable list
 * (e.g. aligned to the end edge inside a Box).
 */
@Composable
expect fun ListVerticalScrollbar(listState: LazyListState, modifier: Modifier = Modifier)
