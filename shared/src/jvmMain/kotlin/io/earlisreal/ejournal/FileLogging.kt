package io.earlisreal.ejournal

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Tees [System.out]/[System.err] to a per-session log file under `~/.ejournal/logs` while still
 * echoing to the original console.
 *
 * Why: the packaged (jpackage) Windows launcher is a GUI-subsystem binary with no console, so the
 * app's many `println`/`printStackTrace` diagnostics would otherwise vanish. With this installed
 * they always land in `ejournal.log` next to the database, regardless of how the app was launched.
 *
 * Uses `java.time` rather than kotlinx-datetime on purpose: this is JVM-only launcher infra, and it
 * sidesteps the kotlinx-datetime 0.7 `Clock` runtime pitfall noted in CLAUDE.md.
 */
object FileLogging {

    /** Number of past sessions kept on disk (current run + this many previous). */
    private const val KEPT_SESSIONS = 3
    private val LINE_STAMP = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    /**
     * Installs the tee. Call once, as early as possible in `main()`. Any failure is swallowed and
     * logged to the original stderr — logging setup must never break app startup.
     */
    fun init(logDir: File = File(System.getProperty("user.home"), ".ejournal/logs")) {
        runCatching {
            logDir.mkdirs()
            rotate(logDir)

            val logFile = File(logDir, "ejournal.log")
            val fileOut = PrintStream(FileOutputStream(logFile, false), /* autoFlush = */ true, Charsets.UTF_8)

            System.setOut(PrintStream(TeeStream(System.out, fileOut), true, Charsets.UTF_8))
            System.setErr(PrintStream(TeeStream(System.err, fileOut), true, Charsets.UTF_8))

            Runtime.getRuntime().addShutdownHook(Thread {
                fileOut.flush()
                fileOut.close()
            })

            println("[log] ===== session started ${LocalDateTime.now()} -> ${logFile.absolutePath} =====")
        }.onFailure {
            System.err.println("[log] failed to initialise file logging: ${it.message}")
        }
    }

    /** Shifts `ejournal.log` -> `ejournal.1.log` -> ... so each launch starts a fresh session file. */
    private fun rotate(logDir: File) {
        val current = File(logDir, "ejournal.log")
        if (!current.exists()) return
        File(logDir, "ejournal.$KEPT_SESSIONS.log").delete()
        for (i in KEPT_SESSIONS - 1 downTo 1) {
            val from = File(logDir, "ejournal.$i.log")
            if (from.exists()) from.renameTo(File(logDir, "ejournal.${i + 1}.log"))
        }
        current.renameTo(File(logDir, "ejournal.1.log"))
    }

    /**
     * Mirrors every byte to the console and the log file, prefixing each line in the file with a
     * timestamp. Writes are synchronised at the array level so concurrent log lines (FX thread,
     * coroutines) don't interleave mid-line.
     */
    private class TeeStream(
        private val console: OutputStream,
        private val file: OutputStream,
    ) : OutputStream() {
        private var atLineStart = true

        private fun write1(b: Int) {
            if (atLineStart && b != '\n'.code) {
                file.write("${LocalDateTime.now().format(LINE_STAMP)} ".toByteArray(Charsets.UTF_8))
                atLineStart = false
            }
            console.write(b)
            file.write(b)
            if (b == '\n'.code) atLineStart = true
        }

        @Synchronized
        override fun write(b: Int) = write1(b)

        @Synchronized
        override fun write(b: ByteArray, off: Int, len: Int) {
            for (i in off until off + len) write1(b[i].toInt() and 0xFF)
        }

        @Synchronized
        override fun flush() {
            console.flush()
            file.flush()
        }
    }
}
