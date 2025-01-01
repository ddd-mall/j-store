package com.jstore.order.saleorder

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.saleorder.properties.UserInfo
import com.jstore.order.saleorder.service.OrderService
import com.jstore.order.saleorder.service.SaleOrderCreateParam
import com.jstore.order.saleorder.validator.CreateParamUserInfoValidator
import com.jstore.order.saleorder.validator.SaleOrderCreateParamValidChain
import com.jstore.order.saleorder.validator.SaleOrderValidChain
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
        SaleOrderValidChain(null),
        MockAddressService()
    )
    private val logger: Logger = LoggerFactory.getLogger(SaleOrderTest::class.java)

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


                districtCode = "110106"
                detailAddress = "MOCK detail address"
            }
        val saleOrder = mockOrderService.createSaleOrder(createParam)
        assertNotNull(saleOrder.getId(), "订单创建后ID仍然为空")
        logger.info("订单创建成功，订单ID： {}", arrayOf(saleOrder.getId()))
        asserter.assertSame("用户信息与创建时不一致", saleOrder.buyerInfo, createParam.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", saleOrder.orderItems?.size, createParam.purchaseItemList?.size)
        saleOrder.orderItems?.forEach { item -> logger.info("订单项目金额： ${item.totalPrice}, 单位：${item.totalPrice.getCurrencyUnit()}") }
        logger.info("订单总金额: ${saleOrder.amount}, 单位：${saleOrder.amount.getCurrencyUnit()}")
        val findOrder = saleOrderRepository.findById(saleOrder.getId()!!)
        assertNotNull(findOrder, "订单没有成功被保存")
        assertSame(OrderPositiveStatus.WAIT_PAY, findOrder.positiveStatus, "订单状态不正确")
        logger.info("订单{}", findOrder)
    }
}