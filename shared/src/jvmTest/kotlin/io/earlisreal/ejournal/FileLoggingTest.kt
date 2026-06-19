package io.earlisreal.ejournal

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FileLoggingTest {

    private fun tempLogDir(): File = Files.createTempDirectory("ejlog").toFile()

    @Test
    fun `tees stdout to a timestamped session log while still echoing to the console`() {
        val dir = tempLogDir()
        val realOut = System.out
        val realErr = System.err
        val console = ByteArrayOutputStream()
        try {
            System.setOut(PrintStream(console, true))
            FileLogging.init(dir)
            println("[test] hello-from-stdout")
            System.out.flush()
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }

        val log = File(dir, "ejournal.log")
        assertTrue(log.exists(), "session log file should be created")
        val contents = log.readText()
        assertContains(contents, "session started", message = "log should include the session banner")
        assertContains(contents, "hello-from-stdout", message = "log should contain teed stdout")
        assertTrue(
            Regex("""\d{2}:\d{2}:\d{2}\.\d{3} \[test] hello-from-stdout""").containsMatchIn(contents),
            "each log line should carry an HH:mm:ss.SSS timestamp prefix",
        )
        assertContains(console.toString(), "hello-from-stdout", message = "console should still receive output")
    }

    @Test
    fun `rotates the previous session log on a fresh launch`() {
        val dir = tempLogDir()
        val realOut = System.out
        val realErr = System.err
        try {
            FileLogging.init(dir)
            println("[test] run-A")
            System.out.flush()

            System.setOut(realOut) // detach the tee before the next launch rotates files
            FileLogging.init(dir)
            println("[test] run-B")
            System.out.flush()
        } finally {
            System.setOut(realOut)
            System.setErr(realErr)
        }

        assertContains(File(dir, "ejournal.log").readText(), "run-B", message = "current log holds the latest session")
        assertContains(File(dir, "ejournal.1.log").readText(), "run-A", message = "previous session rolled to ejournal.1.log")
    }
}
