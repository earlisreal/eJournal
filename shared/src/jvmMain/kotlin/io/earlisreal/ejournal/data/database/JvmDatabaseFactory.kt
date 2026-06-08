package io.earlisreal.ejournal.data.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.earlisreal.ejournal.TradeTransaction
import java.io.File

object JvmDatabaseFactory {

    fun create(): AppDatabase {
        val dbDir = File(System.getProperty("user.home"), ".ejournal").also { it.mkdirs() }
        val dbFile = File(dbDir, "ejournal.db")
        val isNew = !dbFile.exists()
        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        if (isNew) AppDatabase.Schema.create(driver)

        return AppDatabase(
            driver = driver,
            TradeTransactionAdapter = TradeTransaction.Adapter(
                datetimeAdapter = DateTimeAdapter,
                actionAdapter   = ActionAdapter
            )
        )
    }
}
