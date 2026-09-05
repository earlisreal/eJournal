package io.earlisreal.ejournal.domain.alpaca

import io.earlisreal.ejournal.domain.broker.BrokerSyncDetail
import io.earlisreal.ejournal.domain.model.Action
import io.earlisreal.ejournal.domain.model.Transaction
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

private const val PAGE_SIZE = 100
private const val MAX_ATTEMPTS = 3
private val EASTERN = TimeZone.of("America/New_York")
private val UTC = TimeZone.UTC

class AlpacaBrokerClientImpl(
    private val httpClient: HttpClient,
) : AlpacaBrokerClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun testConnection(credentials: AlpacaBrokerCredentials): AlpacaConnectionResult {
        return when (val result = requestWithRetry(credentials, "/v2/account")) {
            is RequestResult.Failure -> AlpacaConnectionResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    AlpacaConnectionResult.InvalidCredentials
                result.status.value >= 400 ->
                    AlpacaConnectionResult.NetworkError(result.errorMessage())
                else -> parseAccount(result.body, credentials.environment)
            }
        }
    }

    override suspend fun fetchFills(
        credentials: AlpacaBrokerCredentials,
        portfolioId: Long,
        after: Instant?,
        until: Instant?,
    ): AlpacaFetchResult {
        val account = when (val result = requestWithRetry(credentials, "/v2/account")) {
            is RequestResult.Failure -> return AlpacaFetchResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    return AlpacaFetchResult.InvalidCredentials
                result.status.value >= 400 ->
                    return AlpacaFetchResult.NetworkError(result.errorMessage())
                else -> when (val parsed = parseAccount(result.body, credentials.environment)) {
                    is AlpacaConnectionResult.Connected -> parsed.account
                    AlpacaConnectionResult.InvalidCredentials -> return AlpacaFetchResult.InvalidCredentials
                    is AlpacaConnectionResult.NetworkError -> return AlpacaFetchResult.NetworkError(parsed.message)
                }
            }
        }

        val usEquitySymbols = when (val result = requestWithRetry(credentials, "/v2/assets") {
            parameter("asset_class", "us_equity")
        }) {
            is RequestResult.Failure -> return AlpacaFetchResult.NetworkError(result.message)
            is RequestResult.Response -> when {
                result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                    return AlpacaFetchResult.InvalidCredentials
                result.status.value >= 400 ->
                    return AlpacaFetchResult.NetworkError(result.errorMessage())
                else -> parseUsEquitySymbols(result.body).getOrElse {
                    return AlpacaFetchResult.NetworkError("Invalid Alpaca asset response")
                }
            }
        }

        val fillRows = when (val result = fetchActivityRows(credentials, "FILL", after, until)) {
            is ActivityRowsResult.Success -> result.rows
            ActivityRowsResult.InvalidCredentials -> return AlpacaFetchResult.InvalidCredentials
            is ActivityRowsResult.Error -> return AlpacaFetchResult.NetworkError(result.message)
        }
        val feeRows = when (val result = fetchActivityRows(credentials, "FEE", after, until)) {
            is ActivityRowsResult.Success -> result.rows
            ActivityRowsResult.InvalidCredentials -> return AlpacaFetchResult.InvalidCredentials
            is ActivityRowsResult.Error -> return AlpacaFetchResult.NetworkError(result.message)
        }

        val fills = fillRows.map { it.toFillActivity(usEquitySymbols) }
        val transactions = mutableListOf<Transaction>()
        var skippedOptions = 0
        var skippedNonEquity = 0
        var skippedMalformed = 0

        for (fill in fills) {
            when (val mapped = fill.toTransactionOrSkip(portfolioId, credentials.environment, account.id)) {
                is FillMapping.Accepted -> transactions += mapped.transaction
                FillMapping.SkipNonEquity -> skippedNonEquity++
                FillMapping.SkipOption -> skippedOptions++
                FillMapping.SkipMalformed -> skippedMalformed++
            }
        }

        return AlpacaFetchResult.Success(
            transactions = transactions,
            account = account,
            detail = BrokerSyncDetail(
                skipped = mapOf(
                    "options" to skippedOptions,
                    "non-US-equity fills" to skippedNonEquity,
                    "malformed fills" to skippedMalformed,
                ).filterValues { it > 0 },
            ),
            fills = fills,
            fees = feeRows.map { it.toFeeActivity() },
        )
    }

    private suspend fun fetchActivityRows(
        credentials: AlpacaBrokerCredentials,
        activityType: String,
        after: Instant?,
        until: Instant?,
    ): ActivityRowsResult {
        val rows = mutableListOf<JsonObject>()
        var pageToken: String? = null
        try {
            do {
                val response = when (val result = requestWithRetry(credentials, "/v2/account/activities/$activityType") {
                    parameter("direction", "asc")
                    parameter("page_size", PAGE_SIZE)
                    after?.let { parameter("after", it.toString()) }
                    until?.let { parameter("until", it.toString()) }
                    pageToken?.let { parameter("page_token", it) }
                }) {
                    is RequestResult.Failure -> return ActivityRowsResult.Error(result.message)
                    is RequestResult.Response -> when {
                        result.status == HttpStatusCode.Unauthorized || result.status == HttpStatusCode.Forbidden ->
                            return ActivityRowsResult.InvalidCredentials
                        result.status.value >= 400 ->
                            return ActivityRowsResult.Error(result.errorMessage())
                        else -> result
                    }
                }
                val page = parseActivityPage(response.body).getOrElse {
                    return ActivityRowsResult.Error("Invalid Alpaca activity response")
                }
                rows += page.rows
                pageToken = page.nextPageToken
            } while (pageToken != null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ActivityRowsResult.Error(e.message ?: "Invalid Alpaca $activityType activity response")
        }
        return ActivityRowsResult.Success(rows)
    }

    private suspend fun requestWithRetry(
        credentials: AlpacaBrokerCredentials,
        path: String,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): RequestResult {
        var backoffMs = 1_000L
        repeat(MAX_ATTEMPTS) { attempt ->
            val response = try {
                val httpResponse = httpClient.get(credentials.environment.tradingBaseUrl + path) {
                    header("APCA-API-KEY-ID", credentials.keyId)
                    header("APCA-API-SECRET-KEY", credentials.secretKey)
                    configure()
                }
                RequestResult.Response(
                    status = httpResponse.status,
                    body = httpResponse.bodyAsText(),
                    requestId = httpResponse.headers["X-Request-ID"],
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return RequestResult.Failure(e.message ?: "Request failed")
            }

            if (response.status.value >= 400) {
                response.requestId?.let { println("[Alpaca] ${response.status} requestId=$it") }
            }
            val retryable = response.status == HttpStatusCode.TooManyRequests || response.status.value >= 500
            if (!retryable || attempt == MAX_ATTEMPTS - 1) return response
            response.requestId?.let { println("[Alpaca] retrying ${response.status} requestId=$it") }
            delay(backoffMs.milliseconds)
            backoffMs *= 2
        }
        return RequestResult.Failure("Request failed")
    }

    private fun parseAccount(body: String, environment: AlpacaEnvironment): AlpacaConnectionResult =
        runCatching { json.parseToJsonElement(body) as? JsonObject ?: error("account is not an object") }
            .fold(
                onSuccess = { root ->
                    val id = root.text("id")?.takeIf { it.isNotBlank() }
                        ?: return@fold AlpacaConnectionResult.NetworkError("Invalid Alpaca account response")
                    AlpacaConnectionResult.Connected(
                        account = AlpacaAccount(id, root.text("account_number"), root.text("status")),
                        environment = environment,
                    )
                },
                onFailure = { AlpacaConnectionResult.NetworkError("Invalid Alpaca account response") },
            )

    private fun parseActivityPage(body: String): Result<RawActivityPage> = runCatching {
        val root = json.parseToJsonElement(body)
        val (elements, envelopeToken) = when (root) {
            is JsonArray -> root to null
            is JsonObject -> {
                val activities = root["activities"] as? JsonArray
                    ?: error("activity response has no activities array")
                activities to root.text("next_page_token")
            }
            else -> error("activity response is not an array or object")
        }
        val rows = elements.map { it as? JsonObject ?: JsonObject(emptyMap()) }
        val nextPageToken = envelopeToken
            ?: rows.lastOrNull()?.text("id")?.takeIf { rows.size == PAGE_SIZE }
        if (rows.size == PAGE_SIZE && nextPageToken.isNullOrBlank()) {
            error("full Alpaca activity page has no next token")
        }
        RawActivityPage(rows, nextPageToken)
    }

    private fun parseUsEquitySymbols(body: String): Result<Set<String>> = runCatching {
        val root = json.parseToJsonElement(body) as? JsonArray
            ?: error("asset response is not an array")
        buildSet {
            root.forEach { element ->
                val asset = element as? JsonObject ?: return@forEach
                val symbol = asset.text("symbol") ?: return@forEach
                val assetClass = asset.text("class") ?: asset.text("asset_class")
                if (assetClass == null || assetClass.equals("us_equity", ignoreCase = true)) {
                    val normalized = symbol.trim().uppercase()
                    add(normalized)
                    add(normalized.replace("/", ""))
                }
            }
        }
    }

    private fun JsonObject.toFillActivity(usEquitySymbols: Set<String>): AlpacaFillActivity {
        val symbol = text("symbol")?.trim()?.takeIf { it.isNotEmpty() }
        val assetClass = when {
            symbol == null -> AlpacaFillAssetClass.OTHER
            isOccOptionSymbol(symbol) -> AlpacaFillAssetClass.OPTION
            symbol.uppercase() in usEquitySymbols -> AlpacaFillAssetClass.US_EQUITY
            else -> AlpacaFillAssetClass.OTHER
        }
        return AlpacaFillActivity(
            id = text("id"),
            symbol = symbol,
            side = text("side"),
            price = number("price"),
            shares = number("qty"),
            transactionTime = text("transaction_time")?.let(::parseInstant),
            assetClass = assetClass,
        )
    }

    private fun JsonObject.toFeeActivity() = AlpacaFeeActivity(
        id = text("id"),
        subtype = text("activity_sub_type") ?: text("activity_subtype"),
        feeDate = text("date")?.let(::parseFeeDate),
        createdAt = (text("created_at") ?: text("at"))?.let(::parseInstant),
        status = text("status"),
        currency = text("currency"),
        netAmount = number("net_amount"),
    )

    private fun AlpacaFillActivity.toTransactionOrSkip(
        portfolioId: Long,
        environment: AlpacaEnvironment,
        accountId: String,
    ): FillMapping {
        if (assetClass == AlpacaFillAssetClass.OPTION) return FillMapping.SkipOption
        if (assetClass != AlpacaFillAssetClass.US_EQUITY) return FillMapping.SkipNonEquity
        val fillId = id?.takeIf { it.isNotBlank() } ?: return FillMapping.SkipMalformed
        val action = when (side?.lowercase()) {
            "buy" -> Action.BUY
            "sell" -> Action.SELL
            else -> return FillMapping.SkipMalformed
        }
        val price = price?.takeIf { it.isFinite() && it > 0.0 } ?: return FillMapping.SkipMalformed
        val shares = shares?.takeIf { it.isFinite() && it > 0.0 } ?: return FillMapping.SkipMalformed
        val transactionTime = transactionTime ?: return FillMapping.SkipMalformed
        val symbol = symbol?.takeIf { it.isNotBlank() } ?: return FillMapping.SkipMalformed
        return FillMapping.Accepted(
            Transaction(
                id = 0L,
                portfolioId = portfolioId,
                symbol = symbol,
                datetime = transactionTime.toLocalDateTime(EASTERN),
                action = action,
                price = price,
                shares = shares,
                fees = 0.0,
                externalId = "alpaca:${environment.name.lowercase()}:$accountId:$fillId",
            )
        )
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.number(key: String): Double? =
        text(key)?.toDoubleOrNull()

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun parseFeeDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.take(10)) }.getOrNull()
            ?: parseInstant(value)?.toLocalDateTime(UTC)?.date

    private fun isOccOptionSymbol(symbol: String): Boolean =
        OCC_OPTION_REGEX.matches(symbol.uppercase())

    private sealed interface FillMapping {
        data class Accepted(val transaction: Transaction) : FillMapping
        data object SkipNonEquity : FillMapping
        data object SkipOption : FillMapping
        data object SkipMalformed : FillMapping
    }

    private data class RawActivityPage(
        val rows: List<JsonObject>,
        val nextPageToken: String?,
    )

    private sealed interface ActivityRowsResult {
        data class Success(val rows: List<JsonObject>) : ActivityRowsResult
        data object InvalidCredentials : ActivityRowsResult
        data class Error(val message: String) : ActivityRowsResult
    }

    private sealed interface RequestResult {
        data class Response(
            val status: HttpStatusCode,
            val body: String,
            val requestId: String?,
        ) : RequestResult {
            fun errorMessage(): String =
                "Alpaca returned HTTP ${status.value}" + (requestId?.let { " (request $it)" } ?: "")
        }

        data class Failure(val message: String) : RequestResult
    }

    companion object {
        // OCC: root (1–6 chars), YYMMDD, call/put, and eight-digit strike. Spaces in the root are
        // optional because Alpaca has returned both padded and unpadded representations.
        private val OCC_OPTION_REGEX = Regex("^[A-Z0-9.]{1,6}\\s*\\d{6}[CP]\\d{8}$")
    }
}
