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
import kotlinx.coroutines.delay
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val BASE_URL = "https://webapi.tradezero.com"
private val EASTERN = TimeZone.of("America/New_York")

@Serializable
private data class AccountsResponse(val accounts: List<AccountRow>)

@Serializable
private data class AccountRow(val account: String)

@Serializable
private data class OrdersResponse(val orders: List<FillRow>)

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
    // Per-fill primary key from the start-date orders endpoint; used to deduplicate re-syncs.
    val tradeId: Long,
    // Intraday fill time ("HH:mm:ss") — tradeDate itself is date-only (midnight).
    val execTime: String? = null,
)

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
            val (accountId, accountBody) = resolveAccountId(creds)
            if (accountId == null) {
                println("[TradeZero] ERROR resolving account: $accountBody")
                return TradeZeroFetchResult.NetworkError("accounts parse error: $accountBody")
            }
            println("[TradeZero] accountId=$accountId  from=$from  to=$to")

            val fills = mutableListOf<FillRow>()
            var weekStart = from
            var weeksDone = 0
            val weeksTotal = generateSequence(from) { it.plus(7, DateTimeUnit.DAY) }.takeWhile { it <= to }.count()

            while (weekStart <= to) {
                val body = fetchWeekWithRetry(creds, accountId, weekStart)
                    ?: return TradeZeroFetchResult.InvalidCredentials
                try {
                    fills += json.decodeFromString<OrdersResponse>(body).orders
                } catch (e: Exception) {
                    println("[TradeZero] ERROR parsing $weekStart: ${e.message}\nbody: ${body.take(800)}")
                    return TradeZeroFetchResult.NetworkError(
                        "orders parse error ($weekStart): ${e.message}\nbody: ${body.take(400)}"
                    )
                }
                weeksDone++
                weekStart = weekStart.plus(7, DateTimeUnit.DAY)
                if (weeksDone % 10 == 0) println("[TradeZero] $weeksDone/$weeksTotal weeks fetched…")
            }

            println("[TradeZero] done — $weeksDone weeks, ${fills.size} raw fills")
            val transactions = fills
                .filter { !it.canceled && it.securityType == "Stock" }
                .map { it.toTransaction(portfolioId) }

            TradeZeroFetchResult.Success(transactions)
        } catch (e: Exception) {
            println("[TradeZero] EXCEPTION ${e::class.simpleName}: ${e.message}")
            e.printStackTrace()
            TradeZeroFetchResult.NetworkError(e.message ?: "Request failed")
        }
    }

    // Returns response body, or null on 404 (auth failure). Retries up to 3 times on 429.
    private suspend fun fetchWeekWithRetry(
        creds: TradeZeroCredentials,
        accountId: String,
        weekStart: LocalDate,
    ): String? {
        val url = "$BASE_URL/v1/api/accounts/$accountId/orders/start-date/$weekStart"
        var delayMs = 1_000L
        repeat(3) { attempt ->
            val response = httpClient.get(url) { addAuthHeaders(creds) }
            val status = response.status
            when {
                status == HttpStatusCode.NotFound -> {
                    println("[TradeZero] 404 on $weekStart — treating as auth failure")
                    return null
                }
                status == HttpStatusCode.TooManyRequests -> {
                    println("[TradeZero] 429 on $weekStart (attempt ${attempt + 1}), retrying in ${delayMs}ms…")
                    delay(delayMs)
                    delayMs *= 2
                }
                status.value >= 400 -> {
                    val body = response.bodyAsText()
                    println("[TradeZero] HTTP $status on $weekStart: $body")
                    return body // let the caller's parse step surface the error
                }
                else -> return response.bodyAsText()
            }
        }
        println("[TradeZero] ERROR gave up on $weekStart after 3 retries")
        return null
    }

    // Returns (accountId, rawBody) — null first element means parse failed, body has the raw response.
    private suspend fun resolveAccountId(creds: TradeZeroCredentials): Pair<String?, String> {
        val response = httpClient.get("$BASE_URL/v1/api/accounts") { addAuthHeaders(creds) }
        val body = response.bodyAsText()
        println("[TradeZero] accounts status=${response.status} body=${body.take(400)}")
        if (response.status != HttpStatusCode.OK) return null to "HTTP ${response.status}: ${body.take(400)}"
        return try {
            json.decodeFromString<AccountsResponse>(body).accounts.firstOrNull()?.account to body
        } catch (e: Exception) {
            println("[TradeZero] accounts parse exception: ${e.message}")
            null to body.take(400)
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.addAuthHeaders(creds: TradeZeroCredentials) {
        header("TZ-API-KEY-ID", creds.keyId)
        header("TZ-API-SECRET-KEY", creds.secretKey)
    }

    private fun FillRow.toTransaction(portfolioId: Long): Transaction {
        val action = if (side == "Buy") Action.BUY else Action.SELL
        return Transaction(
            id          = 0L,
            portfolioId = portfolioId,
            symbol      = symbol,
            datetime    = resolveDatetime(),
            action      = action,
            price       = price,
            shares      = qty,
            fees        = commission + totalFees,
            externalId  = "tz:$tradeId",
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
