package com.jstore.order.service

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.config.TestBeanConfig.orderCreateHandler
import com.jstore.order.config.TestBeanConfig.orderRepository
import com.jstore.order.domain.order.UserInfo
import com.jstore.order.domain.order.command.NormalOrderCreateCMD
import com.jstore.order.domain.order.command.PurchaseItem
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.asserter

class OrderCreateHandlerTest {
    private val logger: Logger = LoggerFactory.getLogger(this::class)

    @Test
    fun orderCreateServiceTest() {
        val createCMD = NormalOrderCreateCMD(
            token = "mock token",
            buyerUserInfo = UserInfo(
                1L,
                PhoneNumber("13312831234"),
                "MockUser——A"
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
            detailAddress = "MOCK detail address"
        )
        val order = orderCreateHandler.create(createCMD)


        assertNotNull(order.id, "订单创建后ID仍然为空")

        logger.info("订单创建成功，订单ID： {}", arrayOf(order.id))
        asserter.assertSame("用户信息与创建时不一致", order.buyerInfo, createCMD.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", order.orderItemImpls.size, createCMD.purchaseItemList.size)
        order.orderItemImpls.forEach { item -> logger.info("订单项目金额： ${item.totalPrice}, 单位：${item.totalPrice.getCurrencyUnit()}") }
        logger.info("订单总金额: ${order.amount}, 单位：${order.amount.getCurrencyUnit()}")
        val findOrder = orderRepository.findById(order.id)
        assertNotNull(findOrder, "订单没有成功被保存")
        logger.info("订单{}", findOrder)
    }
}