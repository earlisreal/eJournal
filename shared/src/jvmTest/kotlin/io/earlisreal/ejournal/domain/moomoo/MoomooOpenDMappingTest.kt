package io.earlisreal.ejournal.domain.moomoo

import com.moomoo.openapi.pb.TrdCommon
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoomooOpenDMappingTest {
    @Test
    fun mapsSdkAccountFieldsAndEligibility() {
        val sdk = TrdCommon.TrdAcc.newBuilder()
            .setAccID(1001L)
            .setUniCardNum("••1001")
            .setSecurityFirm(TrdCommon.SecurityFirm.SecurityFirm_FutuInc_VALUE)
            .setTrdEnv(TrdCommon.TrdEnv.TrdEnv_Real_VALUE)
            .setAccRole(TrdCommon.TrdAccRole.TrdAccRole_Normal_VALUE)
            .setAccStatus(TrdCommon.TrdAccStatus.TrdAccStatus_Active_VALUE)
            .addTrdMarketAuthList(TrdCommon.TrdMarket.TrdMarket_US_VALUE)
            .buildPartial()

        val mapped = mapAccount(sdk)

        assertEquals("1001", mapped.id)
        assertEquals("••1001", mapped.label)
        assertEquals("Moomoo Financial", mapped.securityFirm)
        assertEquals(MoomooAccountEnvironment.REAL, mapped.environment)
        assertEquals(MoomooAccountRole.NORMAL, mapped.role)
        assertEquals(setOf(MoomooMarket.US), mapped.authorizedMarkets)
        assertTrue(mapped.active)
        assertEquals(listOf(mapped), listOf(mapped).eligibleForUsStocks())
    }

    @Test
    fun mapsReadOnlyOrderAndExecutionRows() {
        val order = TrdCommon.Order.newBuilder()
            .setOrderIDEx("order-1")
            .setCode("US.AAPL")
            .setTrdSide(TrdCommon.TrdSide.TrdSide_Sell_VALUE)
            .setCreateTime("2026-06-08 06:51:04")
            .setFillQty(2.0)
            .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
            .buildPartial()
        val execution = TrdCommon.OrderFill.newBuilder()
            .setOrderIDEx("order-1")
            .setCode("US.AAPL")
            .setTrdSide(TrdCommon.TrdSide.TrdSide_Sell_VALUE)
            .setQty(2.0)
            .setPrice(12.5)
            .setCreateTime("2026-06-08 06:52:00")
            .setSecMarket(TrdCommon.TrdSecMarket.TrdSecMarket_US_VALUE)
            .buildPartial()

        val mappedOrder = mapOrder(order)
        val mappedExecution = mapExecution(execution)

        assertEquals("order-1", mappedOrder.id)
        assertEquals(MoomooSide.SELL, mappedOrder.side)
        assertEquals(LocalDateTime.parse("2026-06-08T06:51:04"), mappedOrder.createdAt)
        assertEquals(MoomooMarket.US, mappedOrder.market)
        assertEquals("order-1", mappedExecution.orderId)
        assertEquals(12.5, mappedExecution.price)
        assertEquals(LocalDateTime.parse("2026-06-08T06:52:00"), mappedExecution.executedAt)
    }

    @Test
    fun missingSdkFeeAmountRemainsMissing() {
        val fee = TrdCommon.OrderFee.newBuilder().setOrderIDEx("order-1").build()

        assertNull(mapFee(fee).amount)
        assertEquals("127.0.0.1", MoomooOpenDClient.HOST)
    }
}
