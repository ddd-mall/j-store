package com.jstore.order.domain.order

import com.jstore.common.geo.*
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Locale

/**
 * Order 聚合根使用默认 ShippingInfo（历史数据兼容场景）执行状态转移的单元测试
 *
 * 验证当 ShippingInfo 中收货人信息为默认值（consigneeName=""，ContractInfo(null, null)）时，
 * Order 所有业务流程正常执行。
 *
 * **Validates: Requirement 6.4**
 */
class OrderShippingInfoUnitTest : FunSpec({

    fun createOrderWithDefaultRecipientInfo(): OrderImpl {
        val defaultRecipientInfo = RecipientInfo(
            name = "",
            contractInfo = ContractInfo(null, null),
            shippingAddress = I18nGeoAddress(
                countryCode = CountryCode.CN,
                components = listOf(
                    AddressComponent(
                        code = "110000",
                        level = DivisionLevel(1, "省"),
                        names = mapOf(Locale.SIMPLIFIED_CHINESE to "北京市"),
                        defaultLocale = Locale.SIMPLIFIED_CHINESE,
                    )
                )
            ),
            shippingDetailAddress = "某街道",
        )

        return OrderImpl(
            id = OrderId(1L),
            buyerInfo = UserInfo(uid = 1L, phoneNumber = null, userName = null),
            _items = mutableListOf(
                OrderItemImpl(
                    id = OrderItemId(100L),
                    spuId = 1L,
                    skuId = 1L,
                    goodsName = "测试商品",
                    skuDescription = "规格A",
                    quantity = 1,
                    unitPrice = Price.ofFen(100),
                )
            ),
            recipientInfo = defaultRecipientInfo,
            _tradeStatus = TradeStatus.CREATED,
            _paymentStatus = PaymentStatus.UNPAID,
            _fulfillmentStatus = FulfillmentStatus.UNFULFILLED,
            _afterSaleStatus = AfterSaleStatus.NONE,
            totalAmount = Price.ofFen(100),
            _actualPay = Price.ofFen(100),
        )
    }

    test("正向流程: confirmStock → pay → confirmForShipment → ship → confirmDelivery → complete") {
        val order = createOrderWithDefaultRecipientInfo()

        order.confirmStock().shouldBeInstanceOf<Success<Unit>>()
        order.pay(Price.ofFen(100)).shouldBeInstanceOf<Success<Unit>>()
        order.confirmForShipment().shouldBeInstanceOf<Success<Unit>>()
        order.ship().shouldBeInstanceOf<Success<Unit>>()
        order.confirmDelivery().shouldBeInstanceOf<Success<Unit>>()
        order.complete().shouldBeInstanceOf<Success<Unit>>()
    }

    test("取消流程: confirmStock → cancel") {
        val order = createOrderWithDefaultRecipientInfo()

        order.confirmStock().shouldBeInstanceOf<Success<Unit>>()
        order.cancel(CancellationReason(CancellationCategory.BUYER_CANCELLED, "不想要了"))
            .shouldBeInstanceOf<Success<Unit>>()
    }

    test("退款流程: confirmStock → pay → requestRefund → approveRefund") {
        val order = createOrderWithDefaultRecipientInfo()

        order.confirmStock().shouldBeInstanceOf<Success<Unit>>()
        order.pay(Price.ofFen(100)).shouldBeInstanceOf<Success<Unit>>()

        val itemIds = order.items.map { it.id }
        order.requestRefund(
            RefundReason(RefundCategory.NO_LONGER_NEEDED, "不需要了"),
            itemIds,
        ).shouldBeInstanceOf<Success<Unit>>()

        order.approveRefund(itemIds).shouldBeInstanceOf<Success<Unit>>()
    }

    test("退款拒绝流程: confirmStock → pay → requestRefund → rejectRefund") {
        val order = createOrderWithDefaultRecipientInfo()

        order.confirmStock().shouldBeInstanceOf<Success<Unit>>()
        order.pay(Price.ofFen(100)).shouldBeInstanceOf<Success<Unit>>()

        val itemIds = order.items.map { it.id }
        order.requestRefund(
            RefundReason(RefundCategory.OTHER, "其他原因"),
            itemIds,
        ).shouldBeInstanceOf<Success<Unit>>()

        order.rejectRefund("不符合退款条件", itemIds).shouldBeInstanceOf<Success<Unit>>()
    }
})
