package com.jstore.order.config

import com.jstore.common.framework.DomainEventRegistry
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.StockServiceACL
import com.jstore.order.domain.stock.MockStockRepositoryImpl
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.acl.stock.MockStockServiceACLImpl
import com.jstore.order.domain.risk.RiskFactory
import com.jstore.order.domain.risk.SaleOrderCreateRiskVerifyCmdHandler
import com.jstore.order.domain.saleorder.MockSaleOrderEventPublisher
import com.jstore.order.domain.saleorder.MockSaleOrderRepository
import com.jstore.order.domain.saleorder.NormalSaleOrderCreateCMDHandler
import com.jstore.order.domain.saleorder.NormalSaleOrderFactory
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderRiskValidator
import com.jstore.order.domain.stock.StockFactory
import com.jstore.order.domain.stock.StockPreDeductHandler
import com.jstore.order.domain.stock.StockRepository
import com.jstore.order.service.SaleOrderService
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

object TestBeanConfig {
    private val orderBeansConfig: OrderBeansConfig = OrderBeansConfig()
    val businessExecutor: ThreadPoolTaskExecutor = orderBeansConfig.businessExecutor()
    val domainEventRegistry: DomainEventRegistry = orderBeansConfig.domainEventRegistry(businessExecutor)
    val snowFlakSequence: SnowFlakSequence = SnowFlakSequence()
    val goodsService = MockGoodsService()
    val saleOrderRepository = MockSaleOrderRepository()
    val mockNormalSaleOrderFactory: NormalSaleOrderFactory = NormalSaleOrderFactory(
        goodsService,
        MockAddressService(),
        SaleOrderCreateCMDUserInfoValidator(),
        SaleOrderRiskValidator()
    )

    val normalSaleOrderCreateCMDHandler: NormalSaleOrderCreateCMDHandler = NormalSaleOrderCreateCMDHandler(
        saleOrderRepository,
        mockNormalSaleOrderFactory,
        MockSaleOrderEventPublisher()
    )
    val riskFactory: RiskFactory = RiskFactory()
    val saleOrderCreateRiskVerifyCmdHandler: SaleOrderCreateRiskVerifyCmdHandler = SaleOrderCreateRiskVerifyCmdHandler(riskFactory)
    val stockRepository: StockRepository = MockStockRepositoryImpl()
    val stockServiceACL: StockServiceACL = MockStockServiceACLImpl()
    val stockFactory: StockFactory = StockFactory(stockAclService = stockServiceACL, businessExecutor = businessExecutor)
    val stockPreDeductHandler: StockPreDeductHandler = StockPreDeductHandler(stockRepository, stockFactory)
    val saleOrderService: SaleOrderService = SaleOrderService(normalSaleOrderCreateCMDHandler, saleOrderCreateRiskVerifyCmdHandler, stockPreDeductHandler)
}