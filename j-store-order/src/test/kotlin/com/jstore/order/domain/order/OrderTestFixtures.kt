package com.jstore.order.domain.order

import com.jstore.common.geo.AddressComponent
import com.jstore.common.geo.CountryCode
import com.jstore.common.geo.DivisionLevel
import com.jstore.common.geo.I18nGeoAddress
import com.jstore.common.properties.Price
import java.time.LocalDateTime
import java.util.Locale

internal fun testOrder(
    trade: TradeStatus = TradeStatus.CREATED,
    payment: PaymentStatus = PaymentStatus.UNPAID,
    fulfillment: FulfillmentStatus = FulfillmentStatus.UNFULFILLED,
    itemStatuses: List<OrderItemStatus> = listOf(OrderItemStatus.NONE),
): OrderImpl {
    val items = itemStatuses.mapIndexed { index, status ->
        OrderItemImpl(
            id = OrderItemId((index + 1).toLong()),
            skuId = (index + 10).toLong(),
            spuId = 1,
            goodsName = "商品$index",
            skuDescription = "规格$index",
            quantity = 1,
            unitPrice = Price.ofFen(100),
            status = status,
        )
    }
    return OrderImpl(
        id = OrderId(1),
        merchantId = MerchantId(7),
        buyerInfo = UserInfo(1, null, null),
        _items = items.toMutableList(),
        recipientInfo = RecipientInfo(
            name = "收货人",
            contractInfo = ContractInfo(null, null),
            shippingAddress = I18nGeoAddress(
                CountryCode.CN,
                listOf(
                    AddressComponent(
                        "110000",
                        DivisionLevel(1, "省"),
                        mapOf(Locale.SIMPLIFIED_CHINESE to "北京市"),
                        Locale.SIMPLIFIED_CHINESE,
                    )
                ),
            ),
            shippingDetailAddress = "测试地址",
        ),
        _tradeStatus = trade,
        _paymentStatus = payment,
        _fulfillmentStatus = fulfillment,
        amountSnapshot = OrderAmountSnapshot.cny(Price.ofFen(items.size * 100)),
        _paidAmount = if (payment == PaymentStatus.UNPAID) Price.ZERO else Price.ofFen(items.size * 100),
        _paymentReference = if (payment == PaymentStatus.UNPAID) null else "payment-1",
        _fulfillmentReference = if (fulfillment == FulfillmentStatus.UNFULFILLED) null else "fulfillment-1",
        createTime = LocalDateTime.of(2026, 1, 1, 0, 0),
        _updateTime = LocalDateTime.of(2026, 1, 1, 0, 0),
    )
}
