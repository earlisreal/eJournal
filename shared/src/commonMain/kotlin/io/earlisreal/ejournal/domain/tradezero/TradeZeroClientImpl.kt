package io.earlisreal.ejournal.domain.tradezero

import io.earlisreal.ejournal.data.repository.CredentialsRepository
import io.earlisreal.ejournal.data.repository.TradeZeroCredentials
import io.earlisreal.ejournal.domain.marketdata.ConnectionResult
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds

private const val BASE_URL = "https://webapi.tradezero.com"
private val EASTERN = TimeZone.of("America/New_York")

// orders-with-pagination caps the page size at 100 and the forward span at 365 days. Pages are
// independent (offset/limit), so once the first page reports the total we fetch the rest in parallel.
private const val PAGE_SIZE = 100
private const val MAX_DAYS = 365
private const val MAX_CONCURRENCY = 6

@Serializable
private data class AccountsResponse(val accounts: List<AccountRow>)

@Serializable
private data class AccountRow(val account: String)

@Serializable
private data class PaginatedOrders(val pagination: Pagination, val tradingHistory: List<FillRow>)

@Serializable
private data class Pagination(
    val currentLimit: Int = 0,
    val currentOffset: Int = 0,
    val totalRecords: Int = 0,
    val specifiedSymbol: String? = null,
)

@Serializable
private data class FillRow(
    val symbol: String,
    val securityType: String,
    val side: String,
    val qty: Double,
    val price: Double,
    val commission: Double,
    val totalFees: Double,
    val tradeDate: String,
    val canceled: Boolean,
    // Per-fill primary key. Not used for the externalId (the CSV import has no equivalent, so dedup
    // uses a shared natural key instead); kept for parsing. The endpoint returns extra fields
    // (currency, entryDate, grossProceeds, netProceeds, settleDate, …) ignored via ignoreUnknownKeys.
    val tradeId: Long,
    // Intraday fill time ("HH:mm:ss") — present when tradeDate is date-only (midnight).
    val execTime: String? = null,
)

// Outcome of looking up the trading account, kept distinct so an empty-but-valid response
// (a transient TradeZero hiccup) isn't reported to the user as a parse/network failure.
private sealed interface AccountResolution {
    data class Found(val accountId: String) : AccountResolution
    data object NoAccounts : AccountResolution
    data class Error(val message: String) : AccountResolution
}

class TradeZeroClientImpl(
    private val httpClient: HttpClient,
    private val credentialsRepository: CredentialsRepository,
) : TradeZeroClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(): ConnectionResult {
        val creds = credentialsRepository.getTradeZeroCredentials()
            ?: return ConnectionResult.InvalidKeys
        return runCatching {
            val response = httpClient.get("$BASE_URL/v1/api/accounts") {
                addAuthHeaders(creds)
            }
            if (response.status == HttpStatusCode.NotFound)
                ConnectionResult.InvalidKeys
            else
                ConnectionResult.Connected
        }.getOrElse { ConnectionResult.NetworkError(it.message ?: "Request failed") }
    }

    override suspend fun fetchOrders(portfolioId: Long, from: LocalDate, to: LocalDate): TradeZeroFetchResult {
        val creds = credentialsRepository.getTradeZeroCredentials()
            ?: return TradeZeroFetchResult.InvalidCredentials
        return try {
            val accountId = when (val resolution = resolveAccountId(creds)) {
                is AccountResolution.Found -> resolution.accountId
                AccountResolution.NoAccounts -> {
                    println("[TradeZero] no accounts returned")
                    return TradeZeroFetchResult.NetworkError(
                        "TradeZero returned no accounts — likely a temporary broker issue, try again shortly"
                    )
                }
                is AccountResolution.Error -> {
                    println("[TradeZero] ERROR resolving account: ${resolution.message}")
                    return TradeZeroFetchResult.NetworkError(resolution.message)
                }
            }
            // The endpoint takes a start date plus a forward span (max 365 days). Anchor the window
            // to `to` so a stale/old `from` (e.g. a long-ago last-synced date) never drops the most
            // recent days when the span is clamped.
            val startDate = maxOf(from, to.minus(MAX_DAYS, DateTimeUnit.DAY))
            val numberOfDays = startDate.daysUntil(to).coerceIn(1, MAX_DAYS)
            println("[TradeZero] accountId=$accountId  startDate=$startDate  numberOfDays=$numberOfDays")

            // First page reports the total; the remaining offsets are independent, so fetch them
            // concurrently (bounded) rather than walking the weeks one-by-one.
            val firstBody = fetchPageWithRetry(creds, accountId, startDate, numberOfDays, offset = 0)
                ?: return TradeZeroFetchResult.InvalidCredentials
            val firstPage = parsePage(firstBody)
                ?: return TradeZeroFetchResult.NetworkError("orders parse error: ${firstBody.take(400)}")

            val fills = firstPage.tradingHistory.toMutableList()
            val remainingOffsets = (PAGE_SIZE until firstPage.pagination.totalRecords step PAGE_SIZE).toList()
            if (remainingOffsets.isNotEmpty()) {
                val gate = Semaphore(MAX_CONCURRENCY)
                val bodies = coroutineScope {
                    remainingOffsets.map { offset ->
                        async { gate.withPermit { fetchPageWithRetry(creds, accountId, startDate, numberOfDays, offset) } }
                    }.awaitAll()
                }
                for (body in bodies) {
                    val pageBody = body ?: return TradeZeroFetchResult.InvalidCredentials
                    val parsed = parsePage(pageBody)
                        ?: return TradeZeroFetchResult.NetworkError("orders parse error: ${pageBody.take(400)}")
                    fills += parsed.tradingHistory
                }
            }

            println("[TradeZero] done — ${fills.size} fills (totalRecords=${firstPage.pagination.totalRecords})")
            val externalIds = TradeZeroExternalIdFactory()
            val transactions = fills
                .filter { !it.canceled && it.securityType == "Stock" }
                .map { it.toTransaction(portfolioId, externalIds) }

            TradeZeroFetchResult.Success(transactions)
        } catch (e: Exception) {
            println("[TradeZero] EXCEPTION ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            TradeZeroFetchResult.NetworkError(e.message ?: "Request failed")
        }
    }

    private fun parsePage(body: String): PaginatedOrders? =
        try {
            json.decodeFromString<PaginatedOrders>(body)
        } catch (e: Exception) {
            println("[TradeZero] ERROR parsing page: ${e.message}\nbody: ${body.take(800)}")
            null
        }

    // Returns one page's response body, or null on 404 (auth failure). Retries up to 3 times on 429.
    private suspend fun fetchPageWithRetry(
        creds: TradeZeroCredentials,
        accountId: String,
        startDate: LocalDate,
        numberOfDays: Int,
        offset: Int,
    ): String? {
        val url = "$BASE_URL/v1/api/accounts/$accountId/orders-with-pagination/start-date/$startDate" +
            "?numberOfDays=$numberOfDays&limit=$PAGE_SIZE&offset=$offset"
        var delayMs = 1_000L
        repeat(3) { attempt ->
            val response = httpClient.get(url) { addAuthHeaders(creds) }
            val status = response.status
            when {
                status == HttpStatusCode.NotFound -> {
                    println("[TradeZero] 404 at offset=$offset — treating as auth failure")
                    return null
                }
                status == HttpStatusCode.TooManyRequests -> {
                    println("[TradeZero] 429 at offset=$offset (attempt ${attempt + 1}), retrying in ${delayMs}ms…")
                    delay(delayMs.milliseconds)
                    delayMs *= 2
                }
                status.value >= 400 -> {
                    val body = response.bodyAsText()
                    println("[TradeZero] HTTP $status at offset=$offset: $body")
                    return body // let the caller's parse step surface the error
                }
                else -> return response.bodyAsText()
            }
        }
        println("[TradeZero] ERROR gave up at offset=$offset after 3 retries")
        return null
    }

    // Resolves the trading account id. TradeZero intermittently returns 200 with an empty
    // accounts list (a transient broker hiccup), so we retry that case with backoff — mirroring
    // fetchWeekWithRetry — before giving up. A real HTTP error or unparseable body fails fast.
    private suspend fun resolveAccountId(creds: TradeZeroCredentials): AccountResolution {
        var delayMs = 1_000L
        var lastBody = ""
        repeat(3) { attempt ->
            val response = httpClient.get("$BASE_URL/v1/api/accounts") { addAuthHeaders(creds) }
            val body = response.bodyAsText()
            lastBody = body
            println("[TradeZero] accounts status=${response.status} body=${body.take(400)}")
            if (response.status != HttpStatusCode.OK) {
                return AccountResolution.Error("HTTP ${response.status}: ${body.take(400)}")
            }
            val accountId = try {
                json.decodeFromString<AccountsResponse>(body).accounts.firstOrNull()?.account
            } catch (e: Exception) {
                println("[TradeZero] accounts parse exception: ${e.message}")
                return AccountResolution.Error("accounts parse error: ${body.take(400)}")
            }
            if (accountId != null) return AccountResolution.Found(accountId)
            if (attempt < 2) {
                println("[TradeZero] accounts empty (attempt ${attempt + 1}), retrying in ${delayMs}ms…")
                delay(delayMs.milliseconds)
                delayMs *= 2
            }
        }
        println("[TradeZero] accounts still empty after retries: ${lastBody.take(400)}")
        return AccountResolution.NoAccounts
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addAuthHeaders(creds: TradeZeroCredentials) {
        header("TZ-API-KEY-ID", creds.keyId)
        header("TZ-API-SECRET-KEY", creds.secretKey)
    }

    private fun FillRow.toTransaction(portfolioId: Long, externalIds: TradeZeroExternalIdFactory): Transaction {
        val action = if (side == "Buy") Action.BUY else Action.SELL
        val datetime = resolveDatetime()
        return Transaction(
            id          = 0L,
            portfolioId = portfolioId,
            symbol      = symbol,
            datetime    = datetime,
            action      = action,
            price       = price,
            shares      = qty,
            fees        = commission + totalFees,
            // Natural key shared with the CSV import (tradeId isn't available there), so the same
            // fill from either source dedups. See TradeZeroExternalIdFactory.
            externalId  = externalIds.create(symbol, datetime, action, qty),
        )
    }

    // tradeDate is either a full UTC instant ("…Z"/"…+hh:mm") or a date-only timestamp at midnight,
    // in which case execTime ("HH:mm:ss", exchange/Eastern wall-clock) carries the intraday fill time.
    private fun FillRow.resolveDatetime(): LocalDateTime {
        if (tradeDate.endsWith("Z") || tradeDate.contains("+")) {
            return Instant.parse(tradeDate).toLocalDateTime(EASTERN)
        }
        val date = LocalDateTime.parse(tradeDate).date
        val time = execTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        return if (time != null) LocalDateTime(date, time) else LocalDateTime.parse(tradeDate)
    }
}
