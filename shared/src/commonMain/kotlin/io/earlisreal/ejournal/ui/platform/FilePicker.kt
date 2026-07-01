package io.earlisreal.ejournal.ui.platform

/**
 * Opens the OS-native "open files" dialog filtered to broker import files (CSV / XLSX) and returns
 * the raw bytes of each chosen file. Returns an empty list if the user cancels.
 *
 * Surfaced as expect/actual (same pattern as [io.earlisreal.ejournal.ui.chart.CandlestickChart])
 * so `commonMain` stays free of platform file-dialog APIs. The JVM actual uses FileKit, which opens
 * the modern native dialog (NSOpenPanel on macOS, the Win32 COM IFileOpenDialog on Windows) with the
 * extension filter honored on both. Suspends until the dialog is dismissed; safe to call from a UI
 * coroutine.
 */
expect suspend fun pickImportFiles(): List<ByteArray>
