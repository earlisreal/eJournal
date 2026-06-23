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

    @Test
    fun `fresh install includes the PortfolioSetting table`() {
        val dir = tempDir()
        JvmDatabaseFactory.create(dir)
        assertTrue("PortfolioSetting" in tableNames(File(dir, "ejournal.db")))
    }

    @Test
    fun `migrating a v1 database adds the PortfolioSetting table`() {
        val dir = tempDir()
        val dbFile = File(dir, "ejournal.db")
        // Simulate a v1 database: the full current schema minus the v2 table, stamped as version 1.
        val seed = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        AppDatabase.Schema.create(seed)
        seed.execute(null, "DROP TABLE PortfolioSetting", 0)
        seed.execute(null, "PRAGMA user_version = 1", 0)
        seed.close()
        assertTrue("PortfolioSetting" !in tableNames(dbFile)) // precondition

        JvmDatabaseFactory.create(dir) // detects v1 < v2 and runs the migration

        assertTrue("PortfolioSetting" in tableNames(dbFile))
    }
}
