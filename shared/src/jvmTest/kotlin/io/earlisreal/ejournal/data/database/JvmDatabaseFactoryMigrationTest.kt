package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JvmDatabaseFactoryMigrationTest {

    private fun tempDir(): File = Files.createTempDirectory("ejournal-db-test").toFile()

    private fun tableNames(dbFile: File): Set<String> {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        val names = driver.executeQuery(
            null,
            "SELECT name FROM sqlite_master WHERE type = 'table'",
            { cursor ->
                val result = mutableSetOf<String>()
                while (cursor.next().value) result.add(cursor.getString(0)!!)
                QueryResult.Value(result.toSet())
            },
            0,
        ).value
        driver.close()
        return names
    }

    @Test
    fun `fresh install creates full schema including OhlcvBar`() {
        val dir = tempDir()
        JvmDatabaseFactory.create(dir)
        assertTrue("OhlcvBar" in tableNames(File(dir, "ejournal.db")))
    }

    @Test
    fun `opening an existing database is a no-op`() {
        val dir = tempDir()
        JvmDatabaseFactory.create(dir)
        val db = JvmDatabaseFactory.create(dir) // second open must not re-create or re-migrate
        assertEquals(0, db.portfolioQueries.selectAll().executeAsList().size)
    }
}
