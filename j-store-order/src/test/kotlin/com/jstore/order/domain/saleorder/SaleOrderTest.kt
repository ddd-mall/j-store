package com.jstore.order.domain.saleorder

import com.jstore.common.logging.Logger
import com.jstore.common.logging.LoggerFactory
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.config.TestBeanConfig
import com.jstore.order.domain.saleorder.properties.UserInfo
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderRiskValidator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.asserter

class SaleOrderTest {
    companion object {
        private val logger: Logger = LoggerFactory.getLogger(SaleOrderTest::class.java)
        private val listener = MockSaleOrderCreatedEventListener()
        @JvmStatic
        @BeforeAll
        fun setUp() {
            listener.register(TestBeanConfig.getSimpleDomainEventRegistry())
        }
    }
    private val goodsService = MockGoodsService()
    private val saleOrderRepository = MockSaleOrderRepository()
    private val mockNormalSaleOrderFactory: NormalSaleOrderFactory = NormalSaleOrderFactory(
        goodsService,
        MockAddressService(),
        SaleOrderCreateCMDUserInfoValidator(),
        SaleOrderRiskValidator()
    )
    private val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler = NormalSaleOrderCreateCMDHandler(
        saleOrderRepository,
        mockNormalSaleOrderFactory,
        MockSaleOrderEventPublisher()
    )




    @Test
    fun createSaleOrderTest() {
        val createCMD = NormalSaleOrderCreateCmd("mock token")
            .apply {
                buyerUserInfo = UserInfo(
                    1L,
                    PhoneNumber("13312831234"),
                    "MockUser——A"
                )
                purchaseItemList = listOf(
                    NormalSaleOrderCreateCmd.PurchaseItem().apply {
                        spuId = 1
                        skuId = 1
                        count = BigDecimal.TWO
                    },
                    NormalSaleOrderCreateCmd.PurchaseItem().apply {
                        skuId = 2
                        spuId = 2
                        count = BigDecimal.ONE
                    }
                )


                districtCode = "110106"
                detailAddress = "MOCK detail address"
            }
        val saleOrder = normalSaleOrderCreateCMDHandler.create(createCMD)
        assertNotNull(saleOrder.getId(), "订单创建后ID仍然为空")
        logger.info("订单创建成功，订单ID： {}", arrayOf(saleOrder.getId()))
        asserter.assertSame("用户信息与创建时不一致", saleOrder.buyerInfo, createCMD.buyerUserInfo)
        asserter.assertSame("订单项数量与传参中不一致", saleOrder.orderItems.size, createCMD.purchaseItemList?.size)
        saleOrder.orderItems.forEach { item -> logger.info("订单项目金额： ${item.totalPrice}, 单位：${item.totalPrice.getCurrencyUnit()}") }
        logger.info("订单总金额: ${saleOrder.amount}, 单位：${saleOrder.amount.getCurrencyUnit()}")
        val findOrder = saleOrderRepository.findById(saleOrder.getId()!!)
        assertNotNull(findOrder, "订单没有成功被保存")
        assertSame(OrderPositiveStatus.WAIT_PAY, findOrder.positiveStatus, "订单状态不正确")
        logger.info("订单{}", findOrder)
        Thread.sleep(1000)
    }
}