package io.earlisreal.ejournal.data

import io.earlisreal.ejournal.data.repository.MarketDataRepository
import io.earlisreal.ejournal.domain.analytics.TradeType
import io.earlisreal.ejournal.domain.analytics.classifyTradeType
import io.earlisreal.ejournal.domain.marketdata.Bar
import io.earlisreal.ejournal.domain.marketdata.EtapeImportResult
import io.earlisreal.ejournal.domain.marketdata.EtapeMarketDataImporter
import io.earlisreal.ejournal.domain.marketdata.Timeframe
import io.earlisreal.ejournal.domain.model.ClosedPosition
import io.earlisreal.ejournal.domain.model.Market
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.sqlite.SQLiteConfig
import kotlin.time.Instant

class EtapeMarketDataImporterImpl(
    private val marketDataRepository: MarketDataRepository,
    private val configuredPath: () -> String? = { null },
    private val defaultPath: () -> File = { File(System.getProperty("user.home"), ".eTape/etape.db") },
    private val connectionFactory: (File) -> Connection = ::openReadOnly,
) : EtapeMarketDataImporter {

    override suspend fun importFor(positions: List<ClosedPosition>): EtapeImportResult = withContext(Dispatchers.IO) {
        val keys = positions.asSequence()
            .filter { it.market == Market.US_STOCKS && classifyTradeType(it) == TradeType.DAY }
            .map { PositionDate(it.symbol.uppercase(), it.entryDatetime.date) }
            .distinct()
            .toList()
        if (keys.isEmpty()) return@withContext EtapeImportResult()

        val selectedPath = configuredPath()?.trim()?.takeIf { it.isNotEmpty() }
        val file = if (selectedPath != null) File(selectedPath) else defaultPath()
        if (!file.isFile) {
            return@withContext if (selectedPath == null) {
                EtapeImportResult()
            } else {
                EtapeImportResult(warning = "eTape database not found at ${file.absolutePath}")
            }
        }

        try {
            connectionFactory(file).use { connection ->
                validateSchema(connection)
                var imported = 0
                var skipped = 0
                for (key in keys) {
                    val read = readBars(connection, key)
                    if (read.bars.isNotEmpty()) marketDataRepository.upsertBars(Market.US_STOCKS, read.bars)
                    imported += read.bars.size
                    skipped += read.skippedBars
                }
                EtapeImportResult(
                    importedBars = imported,
                    skippedBars = skipped,
                )
            }
        } catch (e: Exception) {
            println("[eTape] import failed: ${e::class.simpleName}: ${e.message}")
            EtapeImportResult(warning = "eTape import failed: ${e.message ?: "unexpected error"}")
        }
    }

    private fun validateSchema(connection: Connection) {
        val columns = connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA table_info(bars_10s)").use { rows ->
                buildSet {
                    while (rows.next()) add(rows.getString("name"))
                }
            }
        }
        require(REQUIRED_COLUMNS.all { it in columns }) {
            "bars_10s table is missing required columns"
        }
    }

    private fun readBars(connection: Connection, key: PositionDate): ReadResult {
        val from = key.date.atStartOfDayIn(EASTERN).toEpochMilliseconds()
        val to = key.date.plus(DatePeriod(days = 1)).atStartOfDayIn(EASTERN).toEpochMilliseconds()
        val bars = mutableListOf<Bar>()
        var skipped = 0
        connection.prepareStatement(SELECT_BARS).use { statement ->
            statement.setString(1, "US.${key.symbol}")
            statement.setLong(2, from)
            statement.setLong(3, to)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val timestamp = rows.getLong("ts")
                    val timestampNull = rows.wasNull()
                    val open = rows.getDouble("o")
                    val openNull = rows.wasNull()
                    val high = rows.getDouble("h")
                    val highNull = rows.wasNull()
                    val low = rows.getDouble("l")
                    val lowNull = rows.wasNull()
                    val close = rows.getDouble("c")
                    val closeNull = rows.wasNull()
                    val volume = rows.getLong("v")
                    val volumeNull = rows.wasNull()
                    if (
                        timestampNull || timestamp % TEN_SECOND_MILLIS != 0L ||
                        openNull || highNull || lowNull || closeNull || volumeNull ||
                        !open.isFinite() || !high.isFinite() || !low.isFinite() || !close.isFinite() || volume < 0L
                    ) {
                        skipped++
                        continue
                    }
                    val localTimestamp = runCatching { Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(EASTERN) }
                        .getOrNull()
                    if (localTimestamp == null) {
                        skipped++
                        continue
                    }
                    bars += Bar(
                        symbol = key.symbol,
                        timeframe = Timeframe.TEN_SECONDS,
                        timestamp = localTimestamp,
                        open = open,
                        high = high,
                        low = low,
                        close = close,
                        volume = volume,
                    )
                }
            }
        }
        return ReadResult(bars, skipped)
    }

    private data class PositionDate(val symbol: String, val date: LocalDate)
    private data class ReadResult(val bars: List<Bar>, val skippedBars: Int)

    private companion object {
        private const val TEN_SECOND_MILLIS = 10_000L
        private val EASTERN = TimeZone.of("America/New_York")
        private val REQUIRED_COLUMNS = setOf("symbol", "ts", "o", "h", "l", "c", "v")
        private const val SELECT_BARS =
            "SELECT ts, o, h, l, c, v FROM bars_10s WHERE symbol = ? AND ts >= ? AND ts < ? ORDER BY ts ASC"
    }
}

private fun openReadOnly(file: File): Connection {
    val config = SQLiteConfig().apply {
        setReadOnly(true)
        busyTimeout = 5_000
    }
    return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}", config.toProperties()).also {
        it.createStatement().use { statement -> statement.execute("PRAGMA query_only = ON") }
    }
}
