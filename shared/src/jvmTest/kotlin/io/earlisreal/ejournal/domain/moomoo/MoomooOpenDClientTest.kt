package io.earlisreal.ejournal.domain.moomoo

import com.moomoo.openapi.pb.Common
import com.moomoo.openapi.pb.TrdCommon
import com.moomoo.openapi.pb.TrdGetAccList
import com.moomoo.openapi.pb.TrdGetHistoryOrderFillList
import com.moomoo.openapi.pb.TrdGetHistoryOrderList
import com.moomoo.openapi.pb.TrdGetOrderFee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class MoomooOpenDClientTest {
    @Test
    fun correlatesRepliesBySerialWhenTheyArriveOutOfOrder() = runTest {
        val connection = FakeTradeConnection().apply { serials += listOf(11, 12) }
        val session = openSession(connection)

        val first = async { session.getAccounts() }
        val second = async { session.getAccounts() }
        runCurrent()
        connection.reply(12, accountResponse(2))
        connection.reply(11, accountResponse(1))

        assertEquals("1", assertIs<MoomooResult.Success<List<MoomooAccount>>>(first.await()).value.single().id)
        assertEquals("2", assertIs<MoomooResult.Success<List<MoomooAccount>>>(second.await()).value.single().id)
    }

    @Test
    fun duplicateInFlightSerialDoesNotReplaceOriginalCallback() = runTest {
        val connection = FakeTradeConnection().apply { serials += listOf(1, 1) }
        val session = openSession(connection)

        val original = async { session.getAccounts() }
        val duplicate = async { session.getAccounts() }
        runCurrent()
        assertTrue(assertIs<MoomooResult.Failure>(duplicate.await()).message.contains("Duplicate OpenD request serial 1"))
        connection.reply(1, accountResponse(5))

        assertEquals("5", assertIs<MoomooResult.Success<List<MoomooAccount>>>(original.await()).value.single().id)
    }

    @Test
    fun acceptsReplyThatArrivesBeforeAwaitRegistration() = runTest {
        val connection = FakeTradeConnection().apply { replyInsideGet = accountResponse(7) }

        val result = openSession(connection).getAccounts()

        assertEquals("7", assertIs<MoomooResult.Success<List<MoomooAccount>>>(result).value.single().id)
    }

    @Test
    fun timedOutSerialDropsLateReplyAndCannotBeReused() = runTest {
        val connection = FakeTradeConnection()
        val session = openSession(connection, 1.milliseconds)

        assertIs<MoomooResult.Failure>(session.getAccounts())
        connection.reply(1, accountResponse(99))
        connection.serials.addFirst(1)

        val reused = assertIs<MoomooResult.Failure>(session.getAccounts())
        assertTrue(reused.message.contains("reused completed request serial 1"))
    }

    @Test
    fun completedSerialDropsDuplicateReplyAndCannotBeReused() = runTest {
        val connection = FakeTradeConnection().apply { replyInsideGet = accountResponse(1) }
        val session = openSession(connection)
        assertIs<MoomooResult.Success<List<MoomooAccount>>>(session.getAccounts())

        connection.replyInsideGet = null
        connection.reply(1, accountResponse(99))
        connection.serials.addFirst(1)

        val reused = assertIs<MoomooResult.Failure>(session.getAccounts())
        assertTrue(reused.message.contains("reused completed request serial 1"))
    }

    @Test
    fun propagatesProtocolErrorMessage() = runTest {
        val connection = FakeTradeConnection().apply { replyInsideGet = errorResponse("account list denied") }

        val result = assertIs<MoomooResult.Failure>(openSession(connection).getAccounts())

        assertEquals("account list denied", result.message)
    }

    @Test
    fun connectionTimeoutAndCancellationCloseAllocatedConnection() = runTest {
        val timedOut = FakeTradeConnection().apply { connectImmediately = false }
        assertIs<MoomooResult.Failure>(client(timedOut, 1.milliseconds).open(11111))
        assertEquals(1, timedOut.closeCount)

        val cancelled = FakeTradeConnection().apply { connectImmediately = false }
        val opening = async { client(cancelled, 30.seconds).open(11111) }
        runCurrent()
        opening.cancelAndJoin()
        assertEquals(1, cancelled.closeCount)
    }

    @Test
    fun setupAndInitExceptionsCloseAllocatedConnections() = runTest {
        val setupFailure = FakeTradeConnection().apply {
            configureFailure = IllegalStateException("SPI setup failed")
        }

        val setupResult = assertIs<MoomooResult.Failure>(client(setupFailure).open(11111))
        assertEquals("SPI setup failed", setupResult.message)
        assertEquals(1, setupFailure.closeCount)

        val initFailure = FakeTradeConnection().apply {
            connectFailure = IllegalStateException("init failed")
        }
        val initResult = assertIs<MoomooResult.Failure>(client(initFailure).open(11111))
        assertEquals("init failed", initResult.message)
        assertEquals(1, initFailure.closeCount)
    }

    @Test
    fun closeAndDisconnectFailPendingCallbacks() = runTest {
        val closedConnection = FakeTradeConnection()
        val closedSession = openSession(closedConnection)
        val closedRequest = async { closedSession.getAccounts() }
        runCurrent()
        closedSession.close()
        assertTrue(assertIs<MoomooResult.Failure>(closedRequest.await()).message.contains("connection closed"))
        assertEquals(1, closedConnection.closeCount)

        val disconnectedConnection = FakeTradeConnection()
        val disconnectedSession = openSession(disconnectedConnection)
        val disconnectedRequest = async { disconnectedSession.getAccounts() }
        runCurrent()
        disconnectedConnection.disconnect(42)
        assertTrue(assertIs<MoomooResult.Failure>(disconnectedRequest.await()).message.contains("error 42"))
        disconnectedSession.close()
    }

    private suspend fun openSession(
        connection: FakeTradeConnection,
        timeout: Duration = 1.seconds,
    ): MoomooSession = assertIs<MoomooResult.Success<MoomooSession>>(client(connection, timeout).open(11111)).value

    private fun client(
        connection: FakeTradeConnection,
        timeout: Duration = 1.seconds,
    ) = MoomooOpenDClient(timeout, { connection }, {})
}

private class FakeTradeConnection : MoomooTradeConnection {
    lateinit var callbacks: MoomooCallbacks
    val serials = ArrayDeque<Int>()
    var nextSerial = 1
    var connectImmediately = true
    var initResult = true
    var connectFailure: Throwable? = null
    var closeCount = 0
    var configureFailure: Throwable? = null
    var replyInsideGet: TrdGetAccList.Response? = null

    override fun configure(callbacks: MoomooCallbacks) {
        this.callbacks = callbacks
        configureFailure?.let { throw it }
    }

    override fun initConnect(host: String, port: Int): Boolean {
        connectFailure?.let { throw it }
        if (initResult && connectImmediately) callbacks.connectedSuccessfully()
        return initResult
    }

    override fun getAccList(request: TrdGetAccList.Request): Int = takeSerial().also { serial ->
        replyInsideGet?.let { callbacks.receive(serial, it) }
    }

    override fun getHistoryOrderList(request: TrdGetHistoryOrderList.Request) = takeSerial()
    override fun getHistoryOrderFillList(request: TrdGetHistoryOrderFillList.Request) = takeSerial()
    override fun getOrderFee(request: TrdGetOrderFee.Request) = takeSerial()
    override fun close() { closeCount++ }

    fun reply(serial: Int, response: TrdGetAccList.Response) = callbacks.receive(serial, response)
    fun disconnect(errorCode: Long) = callbacks.disconnected(errorCode)

    private fun takeSerial() = if (serials.isEmpty()) nextSerial++ else serials.removeFirst()
}

private fun accountResponse(id: Long): TrdGetAccList.Response = TrdGetAccList.Response.newBuilder()
    .setRetType(Common.RetType.RetType_Succeed_VALUE)
    .setS2C(
        TrdGetAccList.S2C.newBuilder().addAccList(
            TrdCommon.TrdAcc.newBuilder()
                .setAccID(id)
                .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
                .setAccRole(TrdCommon.TrdAccRole.TrdAccRole_Normal_VALUE)
                .setAccStatus(TrdCommon.TrdAccStatus.TrdAccStatus_Active_VALUE)
                .addTrdMarketAuthList(TrdCommon.TrdMarket.TrdMarket_US_VALUE),
        ),
    )
    .build()

private fun errorResponse(message: String): TrdGetAccList.Response = TrdGetAccList.Response.newBuilder()
    .setRetType(Common.RetType.RetType_Failed_VALUE)
    .setRetMsg(message)
    .build()
