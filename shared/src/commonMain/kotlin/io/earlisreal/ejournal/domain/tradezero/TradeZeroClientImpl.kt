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
import kotlin.time.Duration.Companion.milliseconds

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
    // Per-fill primary key from the start-date orders endpoint. No longer used for the externalId
    // (the CSV import has no equivalent, so dedup uses a shared natural key instead); kept for parsing.
    val tradeId: Long,
    // Intraday fill time ("HH:mm:ss") — tradeDate itself is date-only (midnight).
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
                    delay(delayMs.milliseconds)
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
