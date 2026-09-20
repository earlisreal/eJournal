package io.earlisreal.ejournal.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import io.earlisreal.ejournal.domain.update.UpdateManager
import io.earlisreal.ejournal.domain.update.UpdateResult
import io.earlisreal.ejournal.domain.update.UpdateState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.Spacing

@Composable
fun UpdateBanner(state: UpdateState, manager: UpdateManager) {
    val available = state.result as? UpdateResult.Available ?: return
    if (available.dismissed) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(
            buildAnnotatedString {
                append("eJournal ${available.update.version} is available. ")
                available.update.releaseNotes
                    .lineSequence()
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?.take(140)
                    ?.let { append("$it ") }
                withLink(
                    LinkAnnotation.Url(
                        available.update.releaseUrl,
                        TextLinkStyles(style = SpanStyle(color = AppTheme.colors.accent)),
                    ),
                ) { append("View release") }
            },
            color = AppTheme.colors.textPrimary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        AppTextButton(text = "Later", onClick = manager::dismissCurrent)
    }
}
