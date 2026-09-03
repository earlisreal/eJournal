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

    private fun createLegacyTagTables(driver: JdbcSqliteDriver) {
        driver.execute(null, "DROP TABLE PositionTag", 0)
        driver.execute(null, "DROP TABLE Tag", 0)
        driver.execute(
            null,
            """
            CREATE TABLE Tag (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                color TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "CREATE UNIQUE INDEX Tag_name ON Tag(name COLLATE NOCASE)", 0)
        driver.execute(
            null,
            """
            CREATE TABLE PositionTag (
                openingTxId INTEGER NOT NULL,
                tagId INTEGER NOT NULL,
                PRIMARY KEY (openingTxId, tagId)
            )
            """.trimIndent(),
            0,
        )
        driver.execute(null, "CREATE INDEX PositionTag_tag ON PositionTag(tagId)", 0)
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
    fun `fresh install includes the PositionNote table`() {
        val dir = tempDir()
        JvmDatabaseFactory.create(dir)
        assertTrue("PositionNote" in tableNames(File(dir, "ejournal.db")))
    }

    @Test
    fun `migrating a v1 database adds the PortfolioSetting table`() {
        val dir = tempDir()
        val dbFile = File(dir, "ejournal.db")
        // Simulate a v1 database: the full current schema minus the v2 and v3 tables, stamped as version 1.
        val seed = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        AppDatabase.Schema.create(seed)
        createLegacyTagTables(seed)
        seed.execute(null, "DROP TABLE PortfolioSetting", 0)
        seed.execute(null, "DROP TABLE PositionNote", 0)
        seed.execute(null, "PRAGMA user_version = 1", 0)
        seed.close()
        assertTrue("PortfolioSetting" !in tableNames(dbFile)) // precondition

        JvmDatabaseFactory.create(dir) // detects v1 < v4 and runs all migrations

        assertTrue("PortfolioSetting" in tableNames(dbFile))
    }

    @Test
    fun `migrating a v2 database adds the PositionNote table`() {
        val dir = tempDir()
        val dbFile = File(dir, "ejournal.db")
        val seed = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        AppDatabase.Schema.create(seed)
        createLegacyTagTables(seed)
        seed.execute(null, "DROP TABLE PositionNote", 0)
        seed.execute(null, "PRAGMA user_version = 2", 0)
        seed.close()
        assertTrue("PositionNote" !in tableNames(dbFile))

        JvmDatabaseFactory.create(dir)

        assertTrue("PositionNote" in tableNames(dbFile))
    }

    @Test
    fun `migrating v3 clones assigned tags per portfolio and discards invalid rows`() {
        val dir = tempDir()
        val dbFile = File(dir, "ejournal.db")
        val seed = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        AppDatabase.Schema.create(seed)
        createLegacyTagTables(seed)
        seed.execute(
            null,
            "INSERT INTO Portfolio (id, name, market, broker, credentialRef) VALUES (1, 'First', 'US_STOCKS', NULL, 'first')",
            0,
        )
        seed.execute(
            null,
            "INSERT INTO Portfolio (id, name, market, broker, credentialRef) VALUES (2, 'Second', 'US_STOCKS', NULL, 'second')",
            0,
        )
        seed.execute(
            null,
            "INSERT INTO TradeTransaction (id, portfolioId, symbol, datetime, action, price, shares, fees) VALUES (101, 1, 'AAPL', '2026-06-01T09:30', 'BUY', 100.0, 10.0, 1.0)",
            0,
        )
        seed.execute(
            null,
            "INSERT INTO TradeTransaction (id, portfolioId, symbol, datetime, action, price, shares, fees) VALUES (202, 2, 'TSLA', '2026-06-01T09:30', 'BUY', 200.0, 10.0, 1.0)",
            0,
        )
        seed.execute(null, "INSERT INTO Tag (id, name, color) VALUES (1, 'Shared', '#111111')", 0)
        seed.execute(null, "INSERT INTO Tag (id, name, color) VALUES (2, 'First only', '#222222')", 0)
        seed.execute(null, "INSERT INTO Tag (id, name, color) VALUES (3, 'Unused', '#333333')", 0)
        seed.execute(null, "INSERT INTO PositionTag (openingTxId, tagId) VALUES (101, 1)", 0)
        seed.execute(null, "INSERT INTO PositionTag (openingTxId, tagId) VALUES (202, 1)", 0)
        seed.execute(null, "INSERT INTO PositionTag (openingTxId, tagId) VALUES (101, 2)", 0)
        seed.execute(null, "INSERT INTO PositionTag (openingTxId, tagId) VALUES (999, 1)", 0)
        seed.execute(null, "PRAGMA user_version = 3", 0)
        seed.close()

        JvmDatabaseFactory.create(dir)
        val migrated = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        val tags = migrated.executeQuery(
            null,
            "SELECT portfolioId || '|' || name || '|' || color FROM Tag ORDER BY portfolioId, name",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += cursor.getString(0)!!
                QueryResult.Value(result.toList())
            },
            0,
        ).value
        val assignments = migrated.executeQuery(
            null,
            "SELECT PositionTag.openingTxId || '|' || Tag.portfolioId || '|' || Tag.name FROM PositionTag JOIN Tag ON Tag.id = PositionTag.tagId ORDER BY PositionTag.openingTxId, Tag.name",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += cursor.getString(0)!!
                QueryResult.Value(result.toList())
            },
            0,
        ).value
        val index = migrated.executeQuery(
            null,
            "PRAGMA index_list('Tag')",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += "${cursor.getString(1)}:${cursor.getLong(2)}"
                QueryResult.Value(result.toSet())
            },
            0,
        ).value
        val indexColumns = migrated.executeQuery(
            null,
            "PRAGMA index_info('Tag_portfolio_name')",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += cursor.getString(2)!!
                QueryResult.Value(result.toList())
            },
            0,
        ).value
        val tagColumns = migrated.executeQuery(
            null,
            "PRAGMA table_info('Tag')",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += cursor.getString(1)!!
                QueryResult.Value(result.toList())
            },
            0,
        ).value
        val positionTagColumns = migrated.executeQuery(
            null,
            "PRAGMA table_info('PositionTag')",
            { cursor ->
                val result = mutableListOf<String>()
                while (cursor.next().value) result += cursor.getString(1)!!
                QueryResult.Value(result.toList())
            },
            0,
        ).value
        migrated.close()

        assertEquals(listOf("1|First only|#222222", "1|Shared|#111111", "2|Shared|#111111"), tags)
        assertEquals(listOf("101|1|First only", "101|1|Shared", "202|2|Shared"), assignments)
        assertTrue("Tag_portfolio_name:1" in index)
        assertEquals(listOf("portfolioId", "name"), indexColumns)
        assertEquals(listOf("id", "portfolioId", "name", "color"), tagColumns)
        assertEquals(listOf("openingTxId", "tagId"), positionTagColumns)
        assertTrue("TagMigrationMap" !in tableNames(dbFile))
    }
}
