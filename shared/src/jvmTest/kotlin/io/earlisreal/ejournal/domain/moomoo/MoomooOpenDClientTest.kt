package io.earlisreal.ejournal.domain.moomoo

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class MoomooOpenDClientTest {
    @Test
    fun frameCodecHandlesLittleEndianHeaderAndPartialReads() {
        val original = OpenDFrame(
            protocolId = 2001,
            format = OpenDFrameCodec.JSON_FORMAT,
            version = 0,
            serial = 7,
            body = "{\"retType\":0}".encodeToByteArray(),
        )
        val encoded = OpenDFrameCodec.encode(original)
        val decoded = OpenDFrameCodec.read(ChunkedInputStream(encoded, 3))

        assertEquals(original.protocolId, decoded.protocolId)
        assertEquals(original.serial, decoded.serial)
        assertContentEquals(original.body, decoded.body)
        assertEquals('F'.code.toByte(), encoded[0])
        assertEquals('T'.code.toByte(), encoded[1])
    }

    @Test
    fun frameCodecRejectsBadMagicDigestAndOversizedBodies() {
        val frame = OpenDFrame(2001, OpenDFrameCodec.JSON_FORMAT, 0, 1, "{}".encodeToByteArray())
        val badMagic = OpenDFrameCodec.encode(frame).also { it[0] = 'X'.code.toByte() }
        assertFailsWith<IllegalStateException> { OpenDFrameCodec.read(ByteArrayInputStream(badMagic)) }

        val badDigest = OpenDFrameCodec.encode(frame).also { it[OpenDFrameCodec.HEADER_SIZE] = 'x'.code.toByte() }
        assertFailsWith<IllegalStateException> { OpenDFrameCodec.read(ByteArrayInputStream(badDigest)) }

        assertFailsWith<IllegalArgumentException> {
            OpenDFrameCodec.encode(frame.copy(body = ByteArray(16 * 1024 * 1024 + 1)))
        }
    }

    @Test
    fun clientUsesLoopbackJsonProtocolForAccountDiscovery() {
        val server = ServerSocket(0)
        val serverThread = thread(start = true, isDaemon = true) {
            server.use { listener ->
                listener.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val init = OpenDFrameCodec.read(input)
                    assertEquals(1001, init.protocolId)
                    val initRequest = Json.parseToJsonElement(init.body.decodeToString()).toString()
                    assertTrue(initRequest.contains("\"clientID\":\"eJournal\""))
                    output.write(frame(1001, init.serial, """{"retType":0,"s2c":{"keepAliveInterval":300}}"""))
                    output.flush()

                    val accounts = OpenDFrameCodec.read(input)
                    assertEquals(2001, accounts.protocolId)
                    output.write(
                        frame(
                            2001,
                            accounts.serial,
                            """{"retType":0,"s2c":{"accList":[{"trdEnv":1,"accID":"9223372036854775808","trdMarketAuthList":[2],"securityFirm":2,"accStatus":0,"accRole":1,"uniCardNum":"••1001"}]}}""",
                        ),
                    )
                    output.flush()
                }
            }
        }

        val result = runBlocking {
            val opened = MoomooOpenDClient(2.seconds).open(server.localPort)
            assertIs<MoomooResult.Success<MoomooSession>>(opened).value.let { session ->
                try {
                    session.getAccounts()
                } finally {
                    session.close()
                }
            }
        }
        serverThread.join(2_000)

        val account = assertIs<MoomooResult.Success<List<MoomooAccount>>>(result).value.single()
        assertEquals("9223372036854775808", account.id)
        assertEquals("••1001", account.label)
        assertEquals(MoomooAccountEnvironment.REAL, account.environment)
        assertTrue(account.active)
    }

    @Test
    fun emptyHistoricalOrderResponseWithoutOrderListIsAnEmptyResult() {
        val server = ServerSocket(0)
        val serverThread = thread(start = true, isDaemon = true) {
            server.use { listener ->
                listener.accept().use { socket ->
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val init = OpenDFrameCodec.read(input)
                    output.write(frame(1001, init.serial, """{"retType":0,"s2c":{"keepAliveInterval":300}}"""))
                    output.flush()

                    val orders = OpenDFrameCodec.read(input)
                    assertEquals(2221, orders.protocolId)
                    output.write(
                        frame(
                            2221,
                            orders.serial,
                            """{"retType":0,"s2c":{"header":{"trdEnv":1,"accID":"1001","trdMarket":2}}}""",
                        ),
                    )
                    output.flush()
                }
            }
        }

        val result = runBlocking {
            val opened = MoomooOpenDClient(2.seconds).open(server.localPort)
            assertIs<MoomooResult.Success<MoomooSession>>(opened).value.let { session ->
                try {
                    session.getHistoricalOrders("1001", LocalDate(2026, 6, 1), LocalDate(2026, 6, 30))
                } finally {
                    session.close()
                }
            }
        }
        serverThread.join(2_000)

        assertEquals(MoomooResult.Success(emptyList()), result)
    }

    @Test
    fun clientRejectsUnexpectedResponseSerial() {
        val server = ServerSocket(0)
        val serverThread = thread(start = true, isDaemon = true) {
            server.use { listener ->
                listener.accept().use { socket ->
                    val init = OpenDFrameCodec.read(socket.getInputStream())
                    socket.getOutputStream().use { output ->
                        output.write(frame(1001, init.serial + 1, "{\"retType\":0,\"s2c\":{}}"))
                        output.flush()
                    }
                }
            }
        }

        val result = runBlocking { MoomooOpenDClient(1.seconds).open(server.localPort) }
        assertIs<MoomooResult.Failure>(result)
        serverThread.join(2_000)
    }

    private fun frame(protocolId: Int, serial: Int, body: String): ByteArray =
        OpenDFrameCodec.encode(OpenDFrame(protocolId, OpenDFrameCodec.JSON_FORMAT, 0, serial, body.encodeToByteArray()))
}

private class ChunkedInputStream(
    private val bytes: ByteArray,
    private val chunkSize: Int,
) : InputStream() {
    private var offset = 0

    override fun read(): Int = if (offset == bytes.size) -1 else bytes[offset++].toInt() and 0xff

    override fun read(target: ByteArray, start: Int, length: Int): Int {
        if (offset == bytes.size) return -1
        val count = minOf(chunkSize, length, bytes.size - offset)
        bytes.copyInto(target, start, offset, offset + count)
        offset += count
        return count
    }
}
