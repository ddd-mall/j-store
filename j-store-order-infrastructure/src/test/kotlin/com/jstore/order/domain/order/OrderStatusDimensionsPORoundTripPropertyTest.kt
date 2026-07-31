package com.jstore.order.domain.order

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.RecipientInfoPO
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.Locale

class OrderStatusDimensionsPORoundTripPropertyTest : FunSpec({
    val cases = listOf(
        StateCase(TradeStatus.CREATED, PaymentStatus.UNPAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE, listOf(OrderItemStatus.NONE)),
        StateCase(TradeStatus.ACTIVE, PaymentStatus.UNPAID, FulfillmentStatus.UNFULFILLED, AfterSaleStatus.NONE, listOf(OrderItemStatus.NONE)),
        StateCase(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.PENDING_SHIPMENT, AfterSaleStatus.NONE, listOf(OrderItemStatus.NONE)),
        StateCase(TradeStatus.ACTIVE, PaymentStatus.PAID, FulfillmentStatus.DELIVERED, AfterSaleStatus.PROCESSING, listOf(OrderItemStatus.REFUNDING)),
        StateCase(TradeStatus.ACTIVE, PaymentStatus.PARTIALLY_REFUNDED, FulfillmentStatus.DELIVERED, AfterSaleStatus.PARTIALLY_COMPLETED, listOf(OrderItemStatus.CANCELED, OrderItemStatus.REFUNDING)),
        StateCase(TradeStatus.CLOSED, PaymentStatus.REFUNDED, FulfillmentStatus.DELIVERED, AfterSaleStatus.COMPLETED, listOf(OrderItemStatus.CANCELED)),
        StateCase(TradeStatus.COMPLETED, PaymentStatus.PAID, FulfillmentStatus.DELIVERED, AfterSaleStatus.NONE, listOf(OrderItemStatus.SHIPPING_FINISHED)),
    )

    test("all persistable status scenarios round-trip with non-status fields") {
        cases.forEach { case ->
            val po = orderPO(case)
            val domain = OrderRepositoryImpl.Converter.toDomain(po)
            val restored = OrderRepositoryImpl.Converter.toPO(domain)
            restored.tradeStatus shouldBe case.trade
            restored.paymentStatus shouldBe case.payment
            restored.fulfillmentStatus shouldBe case.fulfillment
            restored.afterSaleStatus shouldBe case.afterSale
            restored.items.map { it.status } shouldBe case.items
            restored.items.map { it.previousItemStatus } shouldBe case.items.map {
                if (it == OrderItemStatus.REFUNDING) OrderItemStatus.NONE else null
            }
            restored.totalAmount shouldBe po.totalAmount
            restored.actualPay shouldBe po.actualPay
            restored.createTime shouldBe po.createTime
            restored.updateTime shouldBe po.updateTime
            restored.recipientInfo shouldBe po.recipientInfo
        }
    }

    test("converter rejects illegal persisted state") {
        shouldThrow<IllegalArgumentException> {
            OrderRepositoryImpl.Converter.toDomain(
                orderPO(StateCase(TradeStatus.CREATED, PaymentStatus.PAID, FulfillmentStatus.DELIVERED, AfterSaleStatus.NONE, listOf(OrderItemStatus.NONE)))
            )
        }
    }
})

private data class StateCase(
    val trade: TradeStatus,
    val payment: PaymentStatus,
    val fulfillment: FulfillmentStatus,
    val afterSale: AfterSaleStatus,
    val items: List<OrderItemStatus>,
)

private fun orderPO(case: StateCase): OrderPO {
    val address = I18nGeoAddress(
        CountryCode.CN,
        listOf(AddressComponent("110000", DivisionLevel(1, "省"), mapOf(Locale.SIMPLIFIED_CHINESE to "北京"), Locale.SIMPLIFIED_CHINESE)),
    )
    return OrderPO(
        id = 1,
        buyerUid = 2,
        buyerName = "buyer",
        recipientInfo = RecipientInfoPO("收货人", null, "a@b.com", "CN", "110000", address, "地址"),
        tradeStatus = case.trade,
        paymentStatus = case.payment,
        fulfillmentStatus = case.fulfillment,
        afterSaleStatus = case.afterSale,
        totalAmount = BigDecimal.valueOf(case.items.size * 100L),
        actualPay = BigDecimal.valueOf(case.items.size * 100L),
        createTime = LocalDateTime.of(2026, 1, 1, 1, 2),
        updateTime = LocalDateTime.of(2026, 1, 2, 1, 2),
        items = case.items.mapIndexed { index, status ->
            OrderItemPO(
                id = (index + 1).toLong(), orderId = 1, skuId = index + 10L, spuId = 20,
                goodsName = "商品", skuDescription = "规格", quantity = 1, unitPrice = BigDecimal.valueOf(100),
                status = status,
                previousItemStatus = if (status == OrderItemStatus.REFUNDING) OrderItemStatus.NONE else null,
            )
        }.toMutableList(),
    )
}
