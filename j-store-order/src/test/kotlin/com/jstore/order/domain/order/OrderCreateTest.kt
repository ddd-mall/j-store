package com.jstore.order.domain.order

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber

import com.jstore.order.config.TestBeanConfig.orderRepository
import com.jstore.order.config.TestBeanConfig.orderCreationService
import com.jstore.order.domain.order.command.PurchaseItem
import com.jstore.order.domain.order.command.OrderCreateCmd
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.asserter

class OrderCreateTest {
    private val logger: Logger = LoggerFactory.getLogger(OrderCreateTest::class.java)


    @Test
    fun createOrderTest() {
        val createCMD = OrderCreateCmd(
            "mock token",
            buyerUserInfo = UserInfo(
                uid = 1L,
                phoneNumber = PhoneNumber("13312831234"),
                userName = "MockUser——A"
            ),
            purchaseItemList = listOf(
                PurchaseItem(
                    spuId = 1,
                    skuId = 1,
                    quantity = BigDecimal.TWO,
                ),
                PurchaseItem(
                    skuId = 2,
                    spuId = 2,
                    quantity = BigDecimal.ONE,
                )
            ),
            districtCode = "110106",
            detailAddress = "MOCK detail address",
        )

        val order = orderCreationService.create(createCMD)
        assertNotNull(order.id, "订单创建后ID仍然为空")
        logger.info("订单创建成功，订单ID： {}", arrayOf(order.id))
        asserter.assertSame("用户信息与创建时不一致", order.buyerInfo, createCMD.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", order.orderItems.size, createCMD.purchaseItemList.size)
        order.orderItems.forEach { item -> logger.info("订单项目金额： ${item.totalPrice}, 单位：${item.totalPrice.getCurrencyUnit()}") }
        logger.info("订单总金额: ${order.amount}, 单位：${order.amount.getCurrencyUnit()}")
        val findOrder = orderRepository.findById(order.id)
        assertNotNull(findOrder, "订单没有成功被保存")
        assertSame(OrderStatus.WAIT_PAY, findOrder.status, "订单状态不正确")
        logger.info("订单{}", findOrder)
        Thread.sleep(1000)
    }
}