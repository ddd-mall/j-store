package com.jstore.order.domain.order

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber

import com.jstore.order.config.TestBeanConfig.orderRepository
import com.jstore.order.config.TestBeanConfig.orderCreateHandler
import com.jstore.order.domain.order.command.PurchaseItem
import com.jstore.order.domain.order.command.OrderCreateCMD
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.asserter

class OrderCreateTest {
    private val logger: Logger = LoggerFactory.getLogger(OrderCreateTest::class.java)


    @Test
    fun createOrderTest() {
        val createCMD = OrderCreateCMD(
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

    }
}