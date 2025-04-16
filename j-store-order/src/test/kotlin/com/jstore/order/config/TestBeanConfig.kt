package com.jstore.order.config

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.order.acl.address.MockAddressService
import com.jstore.order.acl.goods.MockGoodsService
import com.jstore.order.acl.stock.MockOuterInventoryServiceServiceACLImpl
import com.jstore.order.domain.order.*
import com.jstore.order.domain.inventory.MockInventoryRepositoryImpl
import com.jstore.order.domain.inventory.InventoryFactory
import com.jstore.order.framwork.SpringMockDomainEventBus
import com.jstore.order.service.InventoryService
import com.jstore.order.service.OrderCreationService

object TestBeanConfig {
    private val orderBeansConfig = OrderBeansConfig()
    val businessExecutor = orderBeansConfig.businessExecutor()

    val snowFlakSequence: SnowFlakSequence = SnowFlakSequence()
    private val goodsService = MockGoodsService()
    val orderRepository = MockOrderRepository()
    private val springMockDomainEventBus = SpringMockDomainEventBus(businessExecutor)
    private val mockDomainEventPublisher = MockDomainEventPublisher(springMockDomainEventBus)
    private val mockOrderFactory = OrderFactory(
        goodsService = goodsService,
        geoAddressService = MockAddressService(),
        snowFlakSequence = snowFlakSequence
    )




    val mockInventoryRepositoryImpl = MockInventoryRepositoryImpl()
    val mockOuterInventoryServiceServiceACLImpl = MockOuterInventoryServiceServiceACLImpl()
    val inventoryFactory = InventoryFactory(
        snowFlakSequence = snowFlakSequence
    )

    val inventoryService = InventoryService(
        inventoryFactory = inventoryFactory,
        inventoryRepository = mockInventoryRepositoryImpl,
        outerInventoryServiceACL = mockOuterInventoryServiceServiceACLImpl,
    )

    val orderCreationService = OrderCreationService(
        orderRepository = orderRepository,
        orderFactory = mockOrderFactory,
        inventoryService = inventoryService,
    )


    init {
        registerListener()
    }


    private fun registerListener() {

        MockOrderCreatedEventListener().let { springMockDomainEventBus.register(it) }
    }
}