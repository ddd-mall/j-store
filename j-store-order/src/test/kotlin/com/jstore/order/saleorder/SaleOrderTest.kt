package com.jstore.order.saleorder

import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.com.jstore.order.saleorder.service.OrderService
import com.jstore.com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.com.jstore.order.saleorder.validator.CreateParamUserInfoValidator
import com.jstore.com.jstore.order.saleorder.validator.SaleOrderCreateParamValidChain
import com.jstore.com.jstore.order.saleorder.validator.SaleOrderValidChain
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.saleorder.properties.UserInfo
import org.junit.jupiter.api.Test
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.asserter


class SaleOrderTest {
    private val goodsService = MockGoodsService()
    private val saleOrderRepository = MockSaleOrderRepository()
    private val mockOrderService: OrderService = OrderService(
        saleOrderRepository,
        goodsService,
        SaleOrderCreateParamValidChain(listOf(CreateParamUserInfoValidator())),
        SaleOrderValidChain(null)
    )

    @Test
    fun createSaleOrderTest() {
        val createParam = SaleOrderCreateParam()
            .apply {
                buyerUserInfo = UserInfo(
                    1L,
                    PhoneNumber("13312831234"),
                    "MockUser——A"
                )
                purchaseItemList = listOf(
                    SaleOrderCreateParam.PurchaseItem().apply {
                        spuId = 1
                        skuId = 1
                        count = 2
                    },
                    SaleOrderCreateParam.PurchaseItem().apply {
                        skuId = 2
                        spuId = 2
                        count = 1
                    }
                )

                deliveryAddressInfo = GeoAddressInfo(
                    "mock",
                    "mock",
                    "mock",
                    "mock",
                    "mock",
                    "mock",
                )
            }
        val saleOrder = mockOrderService.createSaleOrder(createParam)
        assertNotNull(saleOrder.getId(), "订单创建后ID仍然为空")
        asserter.assertSame("用户信息与创建时不一致", saleOrder.buyerInfo, createParam.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", saleOrder.orderItems.size, createParam.purchaseItemList?.size)
        saleOrder.orderItems.forEach {item -> println("订单项目金额： ${item.totalPrice}")}
        println("订单总金额 ${saleOrder.amount}")
        assertNotNull(saleOrderRepository.findById(saleOrder.getId()!!), "订单没有成功被保存")
        assertSame(OrderPositiveStatus.WAIT_PAY, saleOrder.positiveStatus, "订单状态不正确")
    }
}