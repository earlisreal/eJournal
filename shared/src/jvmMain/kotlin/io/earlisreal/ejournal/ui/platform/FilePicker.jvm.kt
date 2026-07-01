package io.earlisreal.ejournal.ui.platform

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.readBytes

/**
 * FileKit opens the modern native open-file dialog per OS — NSOpenPanel on macOS and the Win32 COM
 * IFileOpenDialog on Windows — with the CSV/XLSX extension filter honored on both (unlike
 * java.awt.FileDialog, whose FilenameFilter is ignored on macOS and which falls back to the legacy
 * Win32 common dialog). There is no JavaFX toolkit to start or shut down: openFilePicker is a suspend
 * call that dispatches the native dialog itself and returns once it's dismissed (null on cancel), so
 * it's safe to call straight from a UI coroutine.
 */
actual suspend fun pickImportFiles(): List<ByteArray> {
    // openFilePicker has no title parameter; the native dialog uses its default ("Open"). Multiple()
    // returns null on both cancel and empty selection (takeIfNotEmpty), so `?: emptyList()` preserves
    // the empty-list-on-cancel contract the caller relies on.
    val files = FileKit.openFilePicker(
        type = FileKitType.File("csv", "xlsx"),
        mode = FileKitMode.Multiple(),
    ) ?: return emptyList()

    // readBytes() is a suspend reader; skip files that fail to read rather than aborting the import.
    return files.mapNotNull { file -> runCatching { file.readBytes() }.getOrNull() }
}
