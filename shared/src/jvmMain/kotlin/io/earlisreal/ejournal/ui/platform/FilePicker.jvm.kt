package io.earlisreal.ejournal.ui.platform

import javafx.application.Platform
import javafx.stage.FileChooser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * JavaFX FileChooser gives the modern native dialog on Windows (java.awt.FileDialog falls back to
 * the legacy Win32 common dialog there) and stays native on macOS — with its extension filter
 * actually honored, unlike AWT's FilenameFilter on macOS.
 */
actual suspend fun pickImportFiles(): List<ByteArray> {
    JavaFxToolkit.ensureStarted()

    // FileChooser must run on the JavaFX Application Thread; showOpenMultipleDialog blocks it until
    // the dialog closes. Bridge that to the coroutine without blocking the caller's thread.
    //
    // Known limitation: the dialog is ownerless (no JavaFX Stage to parent to in this Compose/Swing
    // app), so if the calling coroutine is cancelled while the dialog is open — e.g. the user
    // navigates away from the Import screen mid-pick — the native dialog can't be force-closed and
    // stays floating until dismissed manually. resume() after cancellation is a documented no-op on
    // a CancellableContinuation, so no crash and no stale result is delivered; the only cost is the
    // orphaned dialog. Dismissing it would require introducing a hidden owner Stage, which isn't
    // worth the JavaFX-integration risk for this niche flow.
    val files: List<File> = suspendCancellableCoroutine { cont ->
        Platform.runLater {
            val chooser = FileChooser().apply {
                title = "Select CSV or XLSX files"
                extensionFilters.add(
                    FileChooser.ExtensionFilter("Broker exports (CSV, XLSX)", "*.csv", "*.xlsx"),
                )
            }
            val selected = runCatching { chooser.showOpenMultipleDialog(null) }.getOrNull()
            cont.resume(selected ?: emptyList())
        }
    }

    // Read off the FX thread; skip files that fail to read rather than aborting the whole import.
    return withContext(Dispatchers.IO) {
        files.mapNotNull { runCatching { it.readBytes() }.getOrNull() }
    }
}
