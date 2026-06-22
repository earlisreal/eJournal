package io.earlisreal.ejournal.ui.startup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import io.earlisreal.ejournal.ui.theme.AppTheme
import io.earlisreal.ejournal.ui.theme.ThemeMode
import io.earlisreal.ejournal.ui.theme.resolveDarkMode

@Composable
fun StartupErrorWindow(message: String, onRetry: () -> Unit, onQuit: () -> Unit) {
    Window(
        onCloseRequest = onQuit,
        state = rememberWindowState(size = DpSize(520.dp, 300.dp)),
        title = "eJournal — startup error",
    ) {
        AppTheme(darkTheme = resolveDarkMode(ThemeMode.SYSTEM, isSystemInDarkTheme())) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("eJournal failed to start")
                Text(message, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onRetry) { Text("Retry") }
                    OutlinedButton(onClick = onQuit) { Text("Quit") }
                }
            }
        }
    }
}
