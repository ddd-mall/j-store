package com.jstore.order.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.acl.stock.MockStockServiceACLImpl
import com.jstore.order.domain.risk.RiskFactory
import com.jstore.order.domain.risk.SaleOrderCreateRiskVerifyCmdHandler
import com.jstore.order.domain.risk.VerifyWhenSaleOrderPrepareToCreatePolicy
import com.jstore.order.domain.saleorder.*
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderRiskValidator
import com.jstore.order.domain.stock.MockStockRepositoryImpl
import com.jstore.order.domain.stock.PreDeductWhenOrderCreatedPolicy
import com.jstore.order.domain.stock.StockFactory
import com.jstore.order.domain.stock.StockPreDeductHandler
import com.jstore.order.framwork.SpringMockDomainEventBus
import com.jstore.order.service.SaleOrderService

object TestBeanConfig {
    private val orderBeansConfig = OrderBeansConfig()
    val businessExecutor = orderBeansConfig.businessExecutor()

    val snowFlakSequence: SnowFlakSequence = SnowFlakSequence()
    private val goodsService = MockGoodsService()
    val saleOrderRepository = MockSaleOrderRepository()
    private val eventRegistry = SpringMockDomainEventBus(businessExecutor)
    private val saleOrderEventPublisher = MockSaleOrderEventPublisher(eventRegistry)
    private val mockSaleOrderFactory = SaleOrderFactory(
        goodsService = goodsService,
        geoAddressService = MockAddressService(),
        saleOrderCreateCMDValidator = SaleOrderCreateCMDUserInfoValidator(),
        saleOrderRiskValidator = SaleOrderRiskValidator(),
        saleOrderEventPublisher = saleOrderEventPublisher,
        snowFlakSequence = snowFlakSequence
    )


    val saleOrderCreateCMDHandler = SaleOrderCreateCMDHandler(
        saleOrderRepository = saleOrderRepository,
        saleOrderFactory = mockSaleOrderFactory,
    )
    val riskFactory = RiskFactory()
    val saleOrderCreateRiskVerifyCmdHandler = SaleOrderCreateRiskVerifyCmdHandler(
        riskFactory = riskFactory
    )
    val stockRepository = MockStockRepositoryImpl()
    val stockServiceACL = MockStockServiceACLImpl()
    val stockFactory = StockFactory(
        stockServiceACL = stockServiceACL,
        snowFlakSequence = snowFlakSequence
    )
    val stockPreDeductHandler = StockPreDeductHandler(
        stockRepository = stockRepository,
        stockFactory = stockFactory
    )
    val saleOrderService = SaleOrderService(
        saleOrderCreateCMDHandler = saleOrderCreateCMDHandler,

        )


    init {
        registerListener()
    }



    private fun registerListener() {
        VerifyWhenSaleOrderPrepareToCreatePolicy(
            saleOrderCreateRiskVerifyCmdHandler = saleOrderCreateRiskVerifyCmdHandler
        ).let { eventRegistry.register(it) }

        PreDeductWhenOrderCreatedPolicy(
            stockPreDeductHandler = stockPreDeductHandler,
        ).let { eventRegistry.register(it) }

        MockSaleOrderCreatedEventListener().let { eventRegistry.register(it) }
    }
}