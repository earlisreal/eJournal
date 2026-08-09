package io.earlisreal.ejournal.domain.moomoo

import com.moomoo.openapi.MMAPI
import com.moomoo.openapi.MMAPI_Conn
import com.moomoo.openapi.MMAPI_Conn_Trd
import com.moomoo.openapi.MMSPI_Conn
import com.moomoo.openapi.MMSPI_Trd
import com.moomoo.openapi.pb.Common
import com.moomoo.openapi.pb.TrdCommon
import com.moomoo.openapi.pb.TrdGetAccList
import com.moomoo.openapi.pb.TrdGetHistoryOrderFillList
import com.moomoo.openapi.pb.TrdGetHistoryOrderList
import com.moomoo.openapi.pb.TrdGetOrderFee
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal interface MoomooTradeConnection {
    fun configure(callbacks: MoomooCallbacks)
    fun initConnect(host: String, port: Int): Boolean
    fun getAccList(request: TrdGetAccList.Request): Int
    fun getHistoryOrderList(request: TrdGetHistoryOrderList.Request): Int
    fun getHistoryOrderFillList(request: TrdGetHistoryOrderFillList.Request): Int
    fun getOrderFee(request: TrdGetOrderFee.Request): Int
    fun close()
}

private class SdkMoomooTradeConnection(
    private val delegate: MMAPI_Conn_Trd = MMAPI_Conn_Trd(),
) : MoomooTradeConnection {
    override fun configure(callbacks: MoomooCallbacks) {
        delegate.setClientInfo("eJournal", 1)
        delegate.setConnSpi(callbacks)
        delegate.setTrdSpi(callbacks)
    }

    override fun initConnect(host: String, port: Int) = delegate.initConnect(host, port, false)
    override fun getAccList(request: TrdGetAccList.Request) = delegate.getAccList(request)
    override fun getHistoryOrderList(request: TrdGetHistoryOrderList.Request) = delegate.getHistoryOrderList(request)
    override fun getHistoryOrderFillList(request: TrdGetHistoryOrderFillList.Request) =
        delegate.getHistoryOrderFillList(request)
    override fun getOrderFee(request: TrdGetOrderFee.Request) = delegate.getOrderFee(request)
    override fun close() = delegate.close()
}

/** Read-only OpenD bridge. This source deliberately references only account/order/deal/fee calls. */
class MoomooOpenDClient internal constructor(
    private val timeout: Duration,
    private val connectionFactory: () -> MoomooTradeConnection,
    private val initializeSdk: () -> Unit,
) : MoomooClient {

    constructor(timeout: Duration = 20.seconds) : this(
        timeout = timeout,
        connectionFactory = { SdkMoomooTradeConnection() },
        initializeSdk = MoomooSdkRuntime::ensureInitialized,
    )

    override suspend fun open(port: Int): MoomooResult<MoomooSession> {
        if (port !in 1..65535) return MoomooResult.Failure("OpenD port must be between 1 and 65535")
        var connection: MoomooTradeConnection? = null
        return try {
            initializeSdk()
            val callbacks = MoomooCallbacks()
            val activeConnection = connectionFactory()
            connection = activeConnection
            activeConnection.configure(callbacks)
            check(activeConnection.initConnect(HOST, port)) { "Could not start an OpenD connection to $HOST:$port" }
            withTimeout(timeout) { callbacks.connected.await() }
            MoomooResult.Success(MoomooOpenDSession(activeConnection, callbacks, timeout))
        } catch (error: TimeoutCancellationException) {
            runCatching { connection?.close() }
            MoomooResult.Failure("Timed out connecting to OpenD")
        } catch (error: CancellationException) {
            runCatching { connection?.close() }
            throw error
        } catch (error: Exception) {
            runCatching { connection?.close() }
            MoomooResult.Failure(error.message ?: "OpenD connection failed")
        }
    }

    companion object {
        const val HOST = "127.0.0.1"
    }
}

private object MoomooSdkRuntime {
    @Volatile private var initialized = false

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                MMAPI.init()
                initialized = true
            }
        }
    }
}

private class MoomooOpenDSession(
    private val connection: MoomooTradeConnection,
    private val callbacks: MoomooCallbacks,
    private val timeout: Duration,
) : MoomooSession {

    override suspend fun getAccounts(): MoomooResult<List<MoomooAccount>> =
        request<TrdGetAccList.Response, List<MoomooAccount>>(
        send = {
            connection.getAccList(
                TrdGetAccList.Request.newBuilder()
                    .setC2S(
                        TrdGetAccList.C2S.newBuilder()
                            .setUserID(0)
                            .setTrdCategory(TrdCommon.TrdCategory.TrdCategory_Security_VALUE)
                            .setNeedGeneralSecAccount(true),
                    )
                    .build(),
            )
        },
            map = { response -> response.payload()?.getAccListList()?.map(::mapAccount) },
        )

    override suspend fun getHistoricalOrders(
        accountId: String,
        from: LocalDate,
        to: LocalDate,
    ): MoomooResult<List<MoomooOrder>> {
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request<TrdGetHistoryOrderList.Response, List<MoomooOrder>>(
            send = {
                connection.getHistoryOrderList(
                    TrdGetHistoryOrderList.Request.newBuilder()
                        .setC2S(
                            TrdGetHistoryOrderList.C2S.newBuilder()
                                .setHeader(header)
                                .setFilterConditions(filter(from, to)),
                        )
                        .build(),
                )
            },
            map = { response -> response.payload()?.getOrderListList()?.map(::mapOrder) },
        )
    }

    override suspend fun getHistoricalExecutions(
        accountId: String,
        from: LocalDate,
        to: LocalDate,
    ): MoomooResult<List<MoomooExecution>> {
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request<TrdGetHistoryOrderFillList.Response, List<MoomooExecution>>(
            send = {
                connection.getHistoryOrderFillList(
                    TrdGetHistoryOrderFillList.Request.newBuilder()
                        .setC2S(
                            TrdGetHistoryOrderFillList.C2S.newBuilder()
                                .setHeader(header)
                                .setFilterConditions(filter(from, to)),
                        )
                        .build(),
                )
            },
            map = { response -> response.payload()?.getOrderFillListList()?.map(::mapExecution) },
        )
    }

    override suspend fun getOrderFees(accountId: String, orderIds: List<String>): MoomooResult<List<MoomooOrderFee>> {
        if (orderIds.size > MoomooSyncService.MAX_FEE_IDS) {
            return MoomooResult.Failure("Order-fee request exceeds ${MoomooSyncService.MAX_FEE_IDS} ids")
        }
        val header = header(accountId) ?: return MoomooResult.Failure("Invalid Moomoo account id")
        return request<TrdGetOrderFee.Response, List<MoomooOrderFee>>(
            send = {
                connection.getOrderFee(
                    TrdGetOrderFee.Request.newBuilder()
                        .setC2S(
                            TrdGetOrderFee.C2S.newBuilder()
                                .setHeader(header)
                                .addAllOrderIdExList(orderIds),
                        )
                        .build(),
                )
            },
            map = { response -> response.payload()?.getOrderFeeListList()?.map(::mapFee) },
        )
    }

    override fun close() {
        callbacks.failAll("OpenD connection closed")
        connection.close()
    }

    private suspend inline fun <reified Response : Any, Value> request(
        noinline send: () -> Int,
        crossinline map: (Response) -> Value?,
    ): MoomooResult<Value> = try {
        val response = callbacks.await<Response>(send(), timeout)
        map(response)?.let { MoomooResult.Success(it) }
            ?: MoomooResult.Failure(responseError(response))
    } catch (error: TimeoutCancellationException) {
        MoomooResult.Failure("OpenD request timed out")
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        MoomooResult.Failure(error.message ?: "OpenD request failed")
    }

    private fun header(accountId: String): TrdCommon.TrdHeader? =
        accountId.toLongOrNull()?.let {
            TrdCommon.TrdHeader.newBuilder()
                .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
                .setAccID(it)
                .setTrdMarket(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
                .build()
        }

    private fun filter(from: LocalDate, to: LocalDate): TrdCommon.TrdFilterConditions =
        TrdCommon.TrdFilterConditions.newBuilder()
            .setBeginTime("$from 00:00:00")
            .setEndTime("$to 23:59:59")
            .setFilterMarket(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
            .build()
}

internal class MoomooCallbacks : MMSPI_Conn, MMSPI_Trd {
    val connected = CompletableDeferred<Unit>()
    private val lock = Any()
    private val pending = mutableMapOf<Int, CompletableDeferred<Any>>()
    private val early = mutableMapOf<Int, Any>()
    private val retired = mutableSetOf<Int>()
    private var closedError: IllegalStateException? = null

    override fun onInitConnect(conn: MMAPI_Conn, errCode: Long, desc: String) {
        if (errCode == 0L) connectedSuccessfully()
        else connectionFailed(desc.ifBlank { "OpenD connection error $errCode" })
    }

    override fun onDisconnect(conn: MMAPI_Conn, errCode: Long) {
        disconnected(errCode)
    }

    override fun onReply_GetAccList(conn: MMAPI_Conn, serialNo: Int, response: TrdGetAccList.Response) =
        receive(serialNo, response)

    override fun onReply_GetHistoryOrderList(
        conn: MMAPI_Conn,
        serialNo: Int,
        response: TrdGetHistoryOrderList.Response,
    ) = receive(serialNo, response)

    override fun onReply_GetHistoryOrderFillList(
        conn: MMAPI_Conn,
        serialNo: Int,
        response: TrdGetHistoryOrderFillList.Response,
    ) = receive(serialNo, response)

    override fun onReply_GetOrderFee(conn: MMAPI_Conn, serialNo: Int, response: TrdGetOrderFee.Response) =
        receive(serialNo, response)

    suspend inline fun <reified T : Any> await(serialNo: Int, timeout: Duration): T {
        check(serialNo > 0) { "OpenD rejected the request before sending it" }
        val deferred = CompletableDeferred<Any>()
        val immediate = synchronized(lock) {
            closedError?.let { throw it }
            check(serialNo !in retired) { "OpenD reused completed request serial $serialNo" }
            check(serialNo !in pending) { "Duplicate OpenD request serial $serialNo" }
            pending[serialNo] = deferred
            early.remove(serialNo)
        }
        immediate?.let(deferred::complete)
        return try {
            withTimeout(timeout) { deferred.await() as T }
        } finally {
            synchronized(lock) {
                retired += serialNo
                if (pending[serialNo] === deferred) pending.remove(serialNo)
                early.remove(serialNo)
            }
        }
    }

    fun failAll(message: String) {
        val error = IllegalStateException(message)
        if (!connected.isCompleted) connected.completeExceptionally(error)
        val deferreds = synchronized(lock) {
            if (closedError == null) closedError = error
            pending.values.toList().also {
                pending.clear()
                early.clear()
            }
        }
        deferreds.forEach { it.completeExceptionally(error) }
    }

    internal fun connectedSuccessfully() {
        connected.complete(Unit)
    }

    internal fun connectionFailed(message: String) {
        connected.completeExceptionally(IllegalStateException(message))
    }

    internal fun disconnected(errCode: Long) {
        failAll("OpenD disconnected (error $errCode)")
    }

    internal fun receive(serialNo: Int, response: Any) {
        val deferred = synchronized(lock) {
            if (closedError != null || serialNo in retired) return
            pending.remove(serialNo) ?: run {
                early.putIfAbsent(serialNo, response)
                null
            }
        }
        deferred?.complete(response)
    }
}

internal fun mapAccount(account: TrdCommon.TrdAcc): MoomooAccount {
    val id = if (account.hasAccID()) account.accID.toString() else ""
    val label = when {
        account.hasUniCardNum() && account.uniCardNum.isNotBlank() -> account.uniCardNum
        account.hasCardNum() && account.cardNum.isNotBlank() -> account.cardNum
        else -> id
    }
    return MoomooAccount(
        id = id,
        label = label,
        securityFirm = securityFirmName(if (account.hasSecurityFirm()) account.securityFirm else -1),
        environment = when (account.trdEnv) {
            TrdCommon.TrdEnv.TrdEnv_Real_VALUE -> MoomooAccountEnvironment.REAL
            TrdCommon.TrdEnv.TrdEnv_Simulate_VALUE -> MoomooAccountEnvironment.SIMULATE
            else -> MoomooAccountEnvironment.UNKNOWN
        },
        role = when (account.accRole) {
            TrdCommon.TrdAccRole.TrdAccRole_Normal_VALUE -> MoomooAccountRole.NORMAL
            TrdCommon.TrdAccRole.TrdAccRole_Master_VALUE -> MoomooAccountRole.MASTER
            TrdCommon.TrdAccRole.TrdAccRole_IPO_VALUE -> MoomooAccountRole.IPO
            else -> MoomooAccountRole.UNKNOWN
        },
        authorizedMarkets = account.trdMarketAuthListList.mapTo(mutableSetOf()) {
            if (it == TrdCommon.TrdMarket.TrdMarket_US_VALUE) MoomooMarket.US else MoomooMarket.OTHER
        },
        active = account.hasAccStatus() && account.accStatus == TrdCommon.TrdAccStatus.TrdAccStatus_Active_VALUE,
    )
}

internal fun mapOrder(order: TrdCommon.Order): MoomooOrder = MoomooOrder(
    id = orderId(order.hasOrderIDEx(), order.orderIDEx, order.hasOrderID(), order.orderID),
    symbol = if (order.hasCode()) order.code else "",
    side = mapSide(if (order.hasTrdSide()) order.trdSide else -1),
    createdAt = if (order.hasCreateTime()) parseOpenDDateTime(order.createTime) else null,
    filledQuantity = if (order.hasFillQty()) order.fillQty else 0.0,
    market = mapMarket(order.hasSecMarket(), order.secMarket, order.hasTrdMarket(), order.trdMarket),
    isCombo = order.comboLegsCount > 0,
    isPrediction = order.hasCode() && MoomooExternalIdFactory.normalizeSymbol(order.code)
        .startsWith("EC.", ignoreCase = true),
)

internal fun mapExecution(fill: TrdCommon.OrderFill): MoomooExecution = MoomooExecution(
    orderId = orderId(fill.hasOrderIDEx(), fill.orderIDEx, fill.hasOrderID(), fill.orderID),
    symbol = if (fill.hasCode()) fill.code else "",
    side = mapSide(if (fill.hasTrdSide()) fill.trdSide else -1),
    quantity = if (fill.hasQty()) fill.qty else Double.NaN,
    price = if (fill.hasPrice()) fill.price else Double.NaN,
    executedAt = if (fill.hasCreateTime()) parseOpenDDateTime(fill.createTime) else null,
    market = mapMarket(fill.hasSecMarket(), fill.secMarket, fill.hasTrdMarket(), fill.trdMarket),
)

internal fun mapFee(fee: TrdCommon.OrderFee): MoomooOrderFee = MoomooOrderFee(
    orderId = if (fee.hasOrderIDEx()) fee.orderIDEx else "",
    amount = if (fee.hasFeeAmount()) fee.feeAmount else null,
)

private fun orderId(hasEx: Boolean, ex: String, hasNumeric: Boolean, numeric: Long): String =
    ex.takeIf { hasEx && it.isNotBlank() } ?: numeric.toString().takeIf { hasNumeric } ?: ""

private fun parseOpenDDateTime(raw: String): LocalDateTime? =
    runCatching { LocalDateTime.parse(raw.trim().replace(' ', 'T')) }.getOrNull()

private fun mapSide(value: Int): MoomooSide = when (value) {
    TrdCommon.TrdSide.TrdSide_Buy_VALUE -> MoomooSide.BUY
    TrdCommon.TrdSide.TrdSide_Sell_VALUE -> MoomooSide.SELL
    TrdCommon.TrdSide.TrdSide_SellShort_VALUE -> MoomooSide.SELL_SHORT
    TrdCommon.TrdSide.TrdSide_BuyBack_VALUE -> MoomooSide.BUY_BACK
    else -> MoomooSide.UNKNOWN
}

private fun mapMarket(hasSec: Boolean, sec: Int, hasTrade: Boolean, trade: Int): MoomooMarket = when {
    hasSec -> if (sec == TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE) MoomooMarket.US else MoomooMarket.OTHER
    hasTrade && trade == TrdCommon.TrdMarket.TrdMarket_US_VALUE -> MoomooMarket.US
    hasTrade -> MoomooMarket.OTHER
    else -> MoomooMarket.UNKNOWN
}

private fun securityFirmName(value: Int): String = when (value) {
    TrdCommon.SecurityFirm.SecurityFirm_FutuSecurities_VALUE -> "Futu Securities"
    TrdCommon.SecurityFirm.SecurityFirm_FutuInc_VALUE -> "Moomoo Financial"
    TrdCommon.SecurityFirm.SecurityFirm_FutuSG_VALUE -> "Moomoo Singapore"
    TrdCommon.SecurityFirm.SecurityFirm_FutuAU_VALUE -> "Moomoo Australia"
    TrdCommon.SecurityFirm.SecurityFirm_FutuCA_VALUE -> "Moomoo Canada"
    TrdCommon.SecurityFirm.SecurityFirm_FutuMY_VALUE -> "Moomoo Malaysia"
    TrdCommon.SecurityFirm.SecurityFirm_FutuJP_VALUE -> "Moomoo Japan"
    else -> "Unknown"
}

private fun TrdGetAccList.Response.payload(): TrdGetAccList.S2C? =
    if (retType == Common.RetType.RetType_Succeed_VALUE && hasS2C()) s2C else null

private fun TrdGetHistoryOrderList.Response.payload(): TrdGetHistoryOrderList.S2C? =
    if (retType == Common.RetType.RetType_Succeed_VALUE && hasS2C()) s2C else null

private fun TrdGetHistoryOrderFillList.Response.payload(): TrdGetHistoryOrderFillList.S2C? =
    if (retType == Common.RetType.RetType_Succeed_VALUE && hasS2C()) s2C else null

private fun TrdGetOrderFee.Response.payload(): TrdGetOrderFee.S2C? =
    if (retType == Common.RetType.RetType_Succeed_VALUE && hasS2C()) s2C else null

private fun responseError(response: Any): String = when (response) {
    is TrdGetAccList.Response -> response.retMsg.takeIf { response.hasRetMsg() && it.isNotBlank() } ?: "Invalid OpenD account list response"
    is TrdGetHistoryOrderList.Response -> response.retMsg.takeIf { response.hasRetMsg() && it.isNotBlank() } ?: "Invalid OpenD historical order response"
    is TrdGetHistoryOrderFillList.Response -> response.retMsg.takeIf { response.hasRetMsg() && it.isNotBlank() } ?: "Invalid OpenD historical deal response"
    is TrdGetOrderFee.Response -> response.retMsg.takeIf { response.hasRetMsg() && it.isNotBlank() } ?: "Invalid OpenD order fee response"
    else -> "Invalid OpenD response"
}
