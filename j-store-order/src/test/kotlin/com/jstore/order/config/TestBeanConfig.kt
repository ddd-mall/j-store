package com.jstore.order.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.acl.stock.MockStockServiceACLImpl
import com.jstore.order.domain.risk.RiskFactory
import com.jstore.order.domain.risk.SaleOrderCreateRiskVerifyCmdHandler
import com.jstore.order.domain.risk.VerifyWhenSaleOrderPrepareToCreatePolicy
import com.jstore.order.domain.saleorder.MockSaleOrderRepository
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.domain.saleorder.SaleOrderFactory
import com.jstore.order.domain.saleorder.SaleOrderEventPublisherImpl
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderRiskValidator
import com.jstore.order.domain.stock.MockStockRepositoryImpl
import com.jstore.order.domain.stock.PreDeductWhenOrderCreatedPolicy
import com.jstore.order.domain.stock.StockFactory
import com.jstore.order.domain.stock.StockPreDeductHandler
import com.jstore.order.service.SaleOrderService

object TestBeanConfig {
    private val orderBeansConfig = OrderBeansConfig()
    val businessExecutor = orderBeansConfig.businessExecutor()
    val domainEventRegistry = orderBeansConfig.domainEventRegistry(businessExecutor)
    val snowFlakSequence: SnowFlakSequence = SnowFlakSequence()
    val goodsService = MockGoodsService()
    val saleOrderRepository = MockSaleOrderRepository()
    val saleOrderEventPublisher = SaleOrderEventPublisherImpl(
        domainEventRegistry = domainEventRegistry
    )
    val mockSaleOrderFactory = SaleOrderFactory(
        goodsService = goodsService,
        geoAddressService = MockAddressService(),
        saleOrderCreateCMDValidator = SaleOrderCreateCMDUserInfoValidator(),
        saleOrderRiskValidator = SaleOrderRiskValidator(),
        saleOrderEventPublisher = saleOrderEventPublisher,
        snowFlakSequence = snowFlakSequence
    )


    val normalSaleOrderCreateCMDHandler = NormalSaleOrderCreateCMDHandler(
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
        stockAclService = stockServiceACL,
        businessExecutor = businessExecutor,
        snowFlakSequence = snowFlakSequence
    )
    val stockPreDeductHandler = StockPreDeductHandler(
        stockRepository = stockRepository,
        stockFactory = stockFactory
    )
    val saleOrderService = SaleOrderService(
        normalSaleOrderCreateCMDHandler = normalSaleOrderCreateCMDHandler,

    )
    val verifyWhenSaleOrderPrepareToCreatePolicy = VerifyWhenSaleOrderPrepareToCreatePolicy(
        saleOrderCreateRiskVerifyCmdHandler = saleOrderCreateRiskVerifyCmdHandler,
        domainEventRegistry = domainEventRegistry
    )
    val preDeductWhenOrderCreatedPolicy = PreDeductWhenOrderCreatedPolicy(
        stockPreDeductHandler = stockPreDeductHandler,
        domainEventRegistry = domainEventRegistry
    )
}