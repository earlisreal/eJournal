package io.earlisreal.ejournal.domain.moomoo

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime

class MoomooOpenDMappingTest {
    @Test
    fun mapsDocumentedJsonAccountFieldsAndEligibility() {
        val account = mapAccount(Json.parseToJsonElement("""
            {"accID":"1001","uniCardNum":"••1001","securityFirm":2,"trdEnv":1,
             "accRole":1,"accStatus":0,"trdMarketAuthList":[2]}
        """).jsonObject)

        assertEquals("1001", account.id)
        assertEquals("••1001", account.label)
        assertEquals("Moomoo Financial", account.securityFirm)
        assertEquals(MoomooAccountEnvironment.REAL, account.environment)
        assertEquals(MoomooAccountRole.NORMAL, account.role)
        assertEquals(setOf(MoomooMarket.US), account.authorizedMarkets)
        assertTrue(account.active)
        assertEquals(listOf(account), listOf(account).eligibleForUsStocks())
    }

    @Test
    fun mapsReadOnlyOrderExecutionAndExactFeeRows() {
        val order = mapOrder(Json.parseToJsonElement("""
            {"orderID":"123","orderIDEx":"order-1","code":"US.AAPL","trdSide":2,
             "createTime":"2026-06-08 06:51:04","fillQty":2,"secMarket":2}
        """).jsonObject)
        val execution = mapExecution(Json.parseToJsonElement("""
            {"orderIDEx":"order-1","code":"US.AAPL","trdSide":2,"qty":2,
             "price":12.5,"createTime":"2026-06-08 06:52:00","secMarket":2}
        """).jsonObject)
        val fee = mapFee(Json.parseToJsonElement("""{"orderIDEx":"order-1","feeAmount":1.23}""").jsonObject)

        assertEquals("order-1", order.id)
        assertEquals(MoomooSide.SELL, order.side)
        assertEquals(LocalDateTime.parse("2026-06-08T06:51:04"), order.createdAt)
        assertEquals(MoomooMarket.US, order.market)
        assertEquals("order-1", execution.orderId)
        assertEquals(12.5, execution.price)
        assertEquals(LocalDateTime.parse("2026-06-08T06:52:00"), execution.executedAt)
        assertEquals(1.23, fee.amount)
    }

    @Test
    fun missingFeeAmountRemainsMissing() {
        val fee = mapFee(Json.parseToJsonElement("""{"orderIDEx":"order-1"}""").jsonObject)
        assertNull(fee.amount)
        assertEquals("127.0.0.1", MoomooOpenDClient.HOST)
    }
}
