package io.earlisreal.ejournal.domain.moomoo

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The fixed OpenD header. The body is JSON, never protobuf or generated schema code. */
internal data class OpenDFrame(
    val protocolId: Int,
    val format: Byte,
    val version: Byte,
    val serial: Int,
    val body: ByteArray,
)

internal object OpenDFrameCodec {
    const val HEADER_SIZE = 44
    const val JSON_FORMAT: Byte = 1
    private const val MAX_BODY_BYTES = 16 * 1024 * 1024

    fun encode(frame: OpenDFrame): ByteArray {
        require(frame.format == JSON_FORMAT) { "Only JSON OpenD frames are supported" }
        require(frame.version == 0.toByte()) { "Unsupported OpenD protocol version" }
        require(frame.body.size <= MAX_BODY_BYTES) { "OpenD body is too large" }

        val header = java.nio.ByteBuffer.allocate(HEADER_SIZE)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        header.put('F'.code.toByte())
        header.put('T'.code.toByte())
        header.putInt(frame.protocolId)
        header.put(frame.format)
        header.put(frame.version)
        header.putInt(frame.serial)
        header.putInt(frame.body.size)
        header.put(MessageDigest.getInstance("SHA-1").digest(frame.body))
        header.put(ByteArray(8))
        return header.array() + frame.body
    }

    fun read(input: InputStream): OpenDFrame {
        val headerBytes = ByteArray(HEADER_SIZE)
        DataInputStream(input).readFully(headerBytes)
        val header = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        if (header.get().toInt().toChar() != 'F' || header.get().toInt().toChar() != 'T') {
            throw IllegalStateException("Invalid OpenD frame header")
        }
        val protocolId = header.int
        val format = header.get()
        val version = header.get()
        val serial = header.int
        val bodyLength = header.int
        val expectedDigest = ByteArray(20).also(header::get)
        header.position(header.position() + 8)
        if (format != JSON_FORMAT || version != 0.toByte()) {
            throw IllegalStateException("Unsupported OpenD frame format/version")
        }
        if (bodyLength !in 0..MAX_BODY_BYTES) {
            throw IllegalStateException("Invalid OpenD body length: $bodyLength")
        }
        val body = ByteArray(bodyLength)
        DataInputStream(input).readFully(body)
        val actualDigest = MessageDigest.getInstance("SHA-1").digest(body)
        if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
            throw IllegalStateException("OpenD body integrity check failed")
        }
        return OpenDFrame(protocolId, format, version, serial, body)
    }
}

/** Read-only OpenD bridge. It implements only the calls needed by eJournal. */
class MoomooOpenDClient(
    private val timeout: Duration = 20.seconds,
) : MoomooClient {
    override suspend fun open(port: Int): MoomooResult<MoomooSession> {
        if (port !in 1..65535) return MoomooResult.Failure("OpenD port must be between 1 and 65535")
        val socket = Socket()
        var connection: OpenDConnection? = null
        return try {
            val timeoutMillis = timeout.inWholeMilliseconds.coerceIn(1, Int.MAX_VALUE.toLong()).toInt()
            withContext(Dispatchers.IO) {
                socket.connect(InetSocketAddress(HOST, port), timeoutMillis)
                socket.soTimeout = timeoutMillis
            }
            val active = OpenDConnection(socket, timeout)
            connection = active
            active.start()
            active.initialize()
            MoomooResult.Success(MoomooOpenDSession(active))
        } catch (error: java.net.SocketTimeoutException) {
            runCatching { socket.close() }
            MoomooResult.Failure("Timed out connecting to OpenD")
        } catch (error: TimeoutCancellationException) {
            runCatching { connection?.close() ?: socket.close() }
            MoomooResult.Failure("Timed out connecting to OpenD")
        } catch (error: CancellationException) {
            runCatching { connection?.close() ?: socket.close() }
            throw error
        } catch (error: Exception) {
            runCatching { connection?.close() ?: socket.close() }
            MoomooResult.Failure(error.message ?: "OpenD connection failed")
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
    }
}

private class OpenDConnection(
    private val socket: Socket,
    private val timeout: Duration,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val input = DataInputStream(socket.getInputStream())
    private val output = socket.getOutputStream()
    private val writeMutex = Mutex()
    private val pendingLock = Any()
    private val pending = mutableMapOf<Int, CompletableDeferred<OpenDFrame>>()
    private var serial = 0
    private val closed = AtomicBoolean(false)
    private var readerJob: Job? = null
    private var keepAliveJob: Job? = null

    fun start() {
        readerJob = scope.launch {
            try {
                while (isActive) {
                    val frame = withContext(Dispatchers.IO) { OpenDFrameCodec.read(input) }
                    val waiter = synchronized(pendingLock) { pending.remove(frame.serial) }
                    check(waiter != null) { "Unexpected OpenD response serial: ${frame.serial}" }
                    waiter.complete(frame)
                }
            } catch (error: CancellationException) {
                // close() completes waiters and cancels the reader intentionally.
            } catch (error: Exception) {
                fail("OpenD connection lost: ${error.message ?: error::class.simpleName}")
            }
        }
    }

    suspend fun initialize() {
        val response = request(
            protocolId = 1001,
            body = buildJsonObject {
                put("c2s", buildJsonObject {
                    put("clientVer", 1)
                    put("clientID", "eJournal")
                    put("recvNotify", false)
                    put("packetEncAlgo", -1)
                    put("pushProtoFmt", OpenDFrameCodec.JSON_FORMAT.toInt())
                })
            },
        )
        requireSuccess(response)
        val interval = response.obj("s2c")?.int("keepAliveInterval")?.coerceIn(1, 300) ?: 10
        keepAliveJob = scope.launch {
            while (isActive) {
                delay(interval.seconds)
                try {
                    requireSuccess(
                        request(
                            protocolId = 1004,
                            body = buildJsonObject {
                                put("c2s", buildJsonObject {
                                    put("time", Clock.System.now().epochSeconds)
                                })
                            },
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    fail("OpenD keepalive failed: ${error.message ?: error::class.simpleName}")
                    cancel()
                }
            }
        }
    }

    suspend fun request(protocolId: Int, body: JsonObject): JsonObject {
        check(!closed.get()) { "OpenD connection closed" }
        val waiter = CompletableDeferred<OpenDFrame>()
        val requestSerial = synchronized(pendingLock) {
            serial = if (serial == Int.MAX_VALUE) 1 else serial + 1
            pending[serial] = waiter
            serial
        }
        try {
            val bytes = OpenDFrameCodec.encode(
                OpenDFrame(
                    protocolId = protocolId,
                    format = OpenDFrameCodec.JSON_FORMAT,
                    version = 0,
                    serial = requestSerial,
                    body = body.toString().encodeToByteArray(),
                ),
            )
            withContext(Dispatchers.IO) {
                writeMutex.withLock {
                    output.write(bytes)
                    output.flush()
                }
            }
            val frame = withTimeout(timeout) { waiter.await() }
            check(frame.protocolId == protocolId) {
                "Unexpected OpenD response protocol: ${frame.protocolId} for $protocolId"
            }
            return Json.parseToJsonElement(frame.body.decodeToString()).jsonObject
        } finally {
            synchronized(pendingLock) {
                if (pending[requestSerial] === waiter) pending.remove(requestSerial)
            }
        }
    }

    fun close() {
        fail("OpenD connection closed")
    }

    private fun fail(message: String) {
        if (!closed.compareAndSet(false, true)) return
        val error = IllegalStateException(message)
        val waiters = synchronized(pendingLock) {
            pending.values.toList().also { pending.clear() }
        }
        waiters.forEach { it.completeExceptionally(error) }
        keepAliveJob?.cancel()
        readerJob?.cancel()
        scope.cancel()
        runCatching { socket.close() }
    }
}

private class MoomooOpenDSession(
    private val connection: OpenDConnection,
) : MoomooSession {
    override suspend fun getAccounts(): MoomooResult<List<MoomooAccount>> = request(2001) {
        put("userID", 0)
        put("trdCategory", 1)
        put("needGeneralSecAccount", true)
    }.map { root ->
        root.obj("s2c")?.array("accList")?.map { mapAccount(it.jsonObject) }
            ?: error(root.errorMessage("Invalid OpenD account list response"))
    }

    override suspend fun getHistoricalOrders(
        accountId: String,
        from: LocalDate,
        to: LocalDate,
    ): MoomooResult<List<MoomooOrder>> {
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request(2221) {
            put("header", header)
            put("filterConditions", filter(from, to))
        }.map { root ->
            root.obj("s2c")?.array("orderList")?.map { mapOrder(it.jsonObject) }
                ?: error(root.errorMessage("Invalid OpenD historical order response"))
        }
    }

    override suspend fun getHistoricalExecutions(
        accountId: String,
        from: LocalDate,
        to: LocalDate,
    ): MoomooResult<List<MoomooExecution>> {
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request(2222) {
            put("header", header)
            put("filterConditions", filter(from, to))
        }.map { root ->
            root.obj("s2c")?.array("orderFillList")?.map { mapExecution(it.jsonObject) }
                ?: error(root.errorMessage("Invalid OpenD historical deal response"))
        }
    }

    override suspend fun getOrderFees(accountId: String, orderIds: List<String>): MoomooResult<List<MoomooOrderFee>> {
        if (orderIds.size > MoomooSyncService.MAX_FEE_IDS) {
            return MoomooResult.Failure("Order-fee request exceeds ${MoomooSyncService.MAX_FEE_IDS} ids")
        }
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request(2225) {
            put("header", header)
            put("orderIDExList", buildJsonArray { orderIds.forEach { add(JsonPrimitive(it)) } })
        }.map { root ->
            root.obj("s2c")?.array("orderFeeList")?.map { mapFee(it.jsonObject) }
                ?: error(root.errorMessage("Invalid OpenD order fee response"))
        }
    }

    override fun close() = connection.close()

    private suspend fun request(
        protocolId: Int,
        c2s: JsonObjectBuilder.() -> Unit,
    ): MoomooResult<JsonObject> = try {
        val root = connection.request(protocolId, buildJsonObject { put("c2s", buildJsonObject(c2s)) })
        if (root.int("retType") != 0) MoomooResult.Failure(root.errorMessage("OpenD request failed"))
        else MoomooResult.Success(root)
    } catch (error: TimeoutCancellationException) {
        MoomooResult.Failure("OpenD request timed out")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        MoomooResult.Failure(error.message ?: "OpenD request failed")
    }

    private fun header(accountId: String): JsonObject? {
        if (accountId.toULongOrNull() == null) return null
        return buildJsonObject {
            put("trdEnv", 1)
            put("accID", accountId)
            put("trdMarket", 2)
        }
    }

    private fun filter(from: LocalDate, to: LocalDate): JsonObject = buildJsonObject {
        put("beginTime", "$from 00:00:00")
        put("endTime", "$to 23:59:59")
        put("filterMarket", 2)
    }
}

private typealias JsonObjectBuilder = kotlinx.serialization.json.JsonObjectBuilder

private inline fun <T> MoomooResult<JsonObject>.map(transform: (JsonObject) -> T): MoomooResult<T> = when (this) {
    is MoomooResult.Failure -> this
    is MoomooResult.Success -> runCatching { MoomooResult.Success(transform(value)) }
        .getOrElse { MoomooResult.Failure(it.message ?: "Invalid OpenD response") }
}

private fun requireSuccess(root: JsonObject) {
    if (root.int("retType") != 0) throw IllegalStateException(root.errorMessage("OpenD request failed"))
}

private fun JsonObject.errorMessage(fallback: String): String =
    string("retMsg")?.takeIf { it.isNotBlank() } ?: fallback

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int = string(key)?.toIntOrNull() ?: -400
private fun JsonObject.long(key: String): Long? = string(key)?.toLongOrNull()
private fun JsonObject.double(key: String): Double? = string(key)?.toDoubleOrNull()
private fun JsonObject.bool(key: String): Boolean? = string(key)?.toBooleanStrictOrNull()
private fun JsonElement.stringValue(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun mapAccount(account: JsonObject): MoomooAccount {
    val id = account.string("accID").orEmpty()
    val label = account.string("uniCardNum")?.takeIf { it.isNotBlank() }
        ?: account.string("cardNum")?.takeIf { it.isNotBlank() }
        ?: id
    val authorizedMarkets = account.array("trdMarketAuthList")
        ?.mapNotNull { it.stringValue()?.toIntOrNull() }
        ?.mapTo(mutableSetOf()) { if (it == 2) MoomooMarket.US else MoomooMarket.OTHER }
        ?: emptySet()
    return MoomooAccount(
        id = id,
        label = label,
        securityFirm = securityFirmName(account.int("securityFirm")),
        environment = when (account.int("trdEnv")) {
            1 -> MoomooAccountEnvironment.REAL
            0 -> MoomooAccountEnvironment.SIMULATE
            else -> MoomooAccountEnvironment.UNKNOWN
        },
        role = when (account.int("accRole")) {
            1 -> MoomooAccountRole.NORMAL
            2 -> MoomooAccountRole.MASTER
            3 -> MoomooAccountRole.IPO
            else -> MoomooAccountRole.UNKNOWN
        },
        authorizedMarkets = authorizedMarkets,
        active = account.int("accStatus") == 0,
    )
}

internal fun mapOrder(order: JsonObject): MoomooOrder = MoomooOrder(
    id = order.string("orderIDEx").orEmpty().ifBlank { order.string("orderID").orEmpty() },
    symbol = order.string("code").orEmpty(),
    side = mapSide(order.int("trdSide")),
    createdAt = order.string("createTime")?.let(::parseOpenDDateTime),
    filledQuantity = order.double("fillQty") ?: 0.0,
    market = mapMarket(order.intOrNull("secMarket"), order.intOrNull("trdMarket")),
    isCombo = (order.array("comboLegs")?.isNotEmpty() == true),
    isPrediction = order.string("code")?.let { MoomooExternalIdFactory.normalizeSymbol(it) }
        ?.startsWith("EC.", ignoreCase = true) == true,
)

internal fun mapExecution(fill: JsonObject): MoomooExecution = MoomooExecution(
    orderId = fill.string("orderIDEx").orEmpty().ifBlank { fill.string("orderID").orEmpty() },
    symbol = fill.string("code").orEmpty(),
    side = mapSide(fill.int("trdSide")),
    quantity = fill.double("qty") ?: Double.NaN,
    price = fill.double("price") ?: Double.NaN,
    executedAt = fill.string("createTime")?.let(::parseOpenDDateTime),
    market = mapMarket(fill.intOrNull("secMarket"), fill.intOrNull("trdMarket")),
)

internal fun mapFee(fee: JsonObject): MoomooOrderFee = MoomooOrderFee(
    orderId = fee.string("orderIDEx").orEmpty(),
    amount = fee.double("feeAmount"),
)

private fun JsonObject.intOrNull(key: String): Int? = string(key)?.toIntOrNull()

private fun mapSide(value: Int): MoomooSide = when (value) {
    1 -> MoomooSide.BUY
    2 -> MoomooSide.SELL
    3 -> MoomooSide.SELL_SHORT
    4 -> MoomooSide.BUY_BACK
    else -> MoomooSide.UNKNOWN
}

private fun mapMarket(securityMarket: Int?, tradeMarket: Int?): MoomooMarket = when {
    securityMarket == 2 -> MoomooMarket.US
    securityMarket != null -> MoomooMarket.OTHER
    tradeMarket == 2 -> MoomooMarket.US
    tradeMarket != null -> MoomooMarket.OTHER
    else -> MoomooMarket.UNKNOWN
}

private fun securityFirmName(value: Int): String = when (value) {
    1 -> "Futu Securities"
    2 -> "Moomoo Financial"
    3 -> "Moomoo Singapore"
    4 -> "Moomoo Australia"
    5 -> "Moomoo Canada"
    6 -> "Moomoo Malaysia"
    7 -> "Moomoo Japan"
    else -> "Unknown"
}

private fun parseOpenDDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw.trim().replace(' ', 'T')) }.getOrNull()
