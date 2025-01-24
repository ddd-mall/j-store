package com.jstore.order.service

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.config.TestBeanConfig.saleOrderRepository
import com.jstore.order.config.TestBeanConfig.saleOrderService
import com.jstore.order.domain.saleorder.SaleOrderCreateCmd
import com.jstore.order.domain.saleorder.OrderPositiveStatus
import com.jstore.order.domain.saleorder.properties.UserInfo
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.asserter

class SaleOrderServiceTest {
    private val logger: Logger = LoggerFactory.getLogger(this::class)

    @Test
    fun saleOrderCreateServiceTest() {
        val createCMD = SaleOrderCreateCmd("mock token")
            .apply {
                buyerUserInfo = UserInfo(
                    1L,
                    PhoneNumber("13312831234"),
                    "MockUser——A"
                )
                purchaseItemList = listOf(
                    SaleOrderCreateCmd.PurchaseItem().apply {
                        spuId = 1
                        skuId = 1
                        quantity = BigDecimal.TWO
                    },
                    SaleOrderCreateCmd.PurchaseItem().apply {
                        skuId = 2
                        spuId = 2
                        quantity = BigDecimal.ONE
                    }
                )


                districtCode = "110106"
                detailAddress = "MOCK detail address"
            }
        val saleOrder = saleOrderService.create(createCMD)
        assertNotNull(saleOrder.id(), "订单创建后ID仍然为空")

        logger.info("订单创建成功，订单ID： {}", arrayOf(saleOrder.id()))
        asserter.assertSame("用户信息与创建时不一致", saleOrder.buyerInfo, createCMD.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", saleOrder.orderItems.size, createCMD.purchaseItemList.size)
        saleOrder.orderItems.forEach { item -> logger.info("订单项目金额： ${item.totalPrice}, 单位：${item.totalPrice.getCurrencyUnit()}") }
        logger.info("订单总金额: ${saleOrder.amount}, 单位：${saleOrder.amount.getCurrencyUnit()}")
        val findOrder = saleOrderRepository.findById(saleOrder.id())
        assertNotNull(findOrder, "订单没有成功被保存")
        assertSame(OrderPositiveStatus.WAIT_PAY, findOrder.positiveStatus, "订单状态不正确")
        logger.info("订单{}", findOrder)

    }
}