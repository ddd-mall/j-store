package com.jstore.order.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.acl.stock.MockStockServiceACLImpl
import com.jstore.order.domain.saleorder.*
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
    private val springMockDomainEventBus = SpringMockDomainEventBus(businessExecutor)
    private val mockDomainEventPublisher = MockDomainEventPublisher(springMockDomainEventBus)
    private val mockSaleOrderFactory = SaleOrderFactory(
        goodsService = goodsService,
        geoAddressService = MockAddressService(),
        snowFlakSequence = snowFlakSequence
    )


    val saleOrderCreateCMDHandler = SaleOrderCreateCMDHandler(
        saleOrderRepository = saleOrderRepository,
        saleOrderFactory = mockSaleOrderFactory,
        domainEventPublisher = mockDomainEventPublisher,
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


        PreDeductWhenOrderCreatedPolicy(
            stockPreDeductHandler = stockPreDeductHandler,
        ).let { springMockDomainEventBus.register(it) }

        MockSaleOrderCreatedEventListener().let { springMockDomainEventBus.register(it) }
    }
}