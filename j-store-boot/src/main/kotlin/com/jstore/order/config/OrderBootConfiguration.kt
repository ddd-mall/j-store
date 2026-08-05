package com.jstore.com.jstore.order.config

import com.jstore.common.framework.event.*
import com.jstore.common.geo.GeoAddressService
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.goods.api.GoodsSnapshotQueryService
import com.jstore.goods.service.AfterSaleStockRestoreEventHandler
import com.jstore.goods.service.InventoryService
import com.jstore.order.acl.GoodsService
import com.jstore.order.acl.GoodsServiceImpl
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderFactoryImpl
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.service.AfterSaleApplicationService
import com.jstore.order.service.FulfillmentDeliveredOrderHandler
import com.jstore.order.service.FulfillmentDispatchedOrderHandler
import com.jstore.order.service.FulfillmentPreparedOrderHandler
import com.jstore.order.service.OrderService
import com.jstore.order.service.OrderStockConfirmedEventHandler
import com.jstore.order.service.OrderStockInsufficientEventHandler
import com.jstore.order.service.PaymentCapturedOrderHandler
import com.jstore.order.service.PaymentRefundFailedOrderHandler
import com.jstore.order.service.PaymentRefundSucceededOrderHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OrderBootConfiguration {
    @Bean
    fun snowFlakSequence(): SnowFlakSequence {
        return SnowFlakSequence()
    }

    @Bean
    fun goodsService(goodsSnapshotQueryService: GoodsSnapshotQueryService): GoodsService {
        return GoodsServiceImpl(goodsSnapshotQueryService)
    }

    @Bean
    fun orderFactory(
        snowFlakSequence: SnowFlakSequence,
        goodsService: GoodsService,
        geoAddressService: GeoAddressService,
    ): OrderFactory {
        return OrderFactoryImpl(
            snowFlakSequence,
            goodsService,
            geoAddressService,
        )
    }

    @Bean
    fun orderService(
        orderFactory: OrderFactory,
        orderRepository: OrderRepository,
        domainEventPublisher: DomainEventPublisher,
    ): OrderService {
        return OrderService(
            orderFactory,
            orderRepository,
            domainEventPublisher,
        )
    }

    @Bean
    fun paymentCapturedOrderHandler(service: OrderService) = PaymentCapturedOrderHandler(service)

    @Bean
    fun fulfillmentPreparedOrderHandler(service: OrderService) =
        FulfillmentPreparedOrderHandler(service)

    @Bean
    fun fulfillmentDispatchedOrderHandler(service: OrderService) =
        FulfillmentDispatchedOrderHandler(service)

    @Bean
    fun fulfillmentDeliveredOrderHandler(service: OrderService) =
        FulfillmentDeliveredOrderHandler(service)

    @Bean
    fun paymentRefundSucceededOrderHandler(
        afterSales: AfterSaleApplicationService,
        orders: OrderService,
    ) = PaymentRefundSucceededOrderHandler(afterSales, orders)

    @Bean
    fun paymentRefundFailedOrderHandler(afterSales: AfterSaleApplicationService) =
        PaymentRefundFailedOrderHandler(afterSales)

    @Bean
    fun orderStockConfirmedHandler(service: OrderService) = OrderStockConfirmedEventHandler(service)

    @Bean
    fun orderStockInsufficientHandler(service: OrderService) =
        OrderStockInsufficientEventHandler(service)

    @Bean
    fun afterSaleFactory(snowFlakSequence: SnowFlakSequence): AfterSaleFactory =
        AfterSaleFactoryImpl(snowFlakSequence)

    @Bean
    fun afterSaleApplicationService(
        factory: AfterSaleFactory,
        repository: AfterSaleRepository,
        orderRepository: OrderRepository,
    ) = AfterSaleApplicationService(factory, repository, orderRepository)

    @Bean
    fun afterSaleStockRestoreEventHandler(inventoryServices: ObjectProvider<InventoryService>) =
        AfterSaleStockRestoreEventHandler {
            inventoryServices.getIfAvailable()
        }

    @Bean
    fun springDomainEventListenerRegistry(
        applicationContext: ConfigurableApplicationContext,
        consumptionRepositoryProvider: ObjectProvider<DomainEventConsumptionRepository>,
    ): SpringDomainEventListenerRegistry {
        return SpringDomainEventListenerRegistry(
            applicationContext,
            consumptionRepositoryProvider.getIfAvailable() ?: NoopDomainEventConsumptionRepository,
        )
    }

    @Bean
    fun localDomainEventBus(
        springDomainEventRegistry: SpringDomainEventListenerRegistry,
        applicationEventPublisher: ApplicationEventPublisher,
    ): LocalDomainEventBus {
        return SpringLocalDomainEventBus(springDomainEventRegistry, applicationEventPublisher)
    }

    @Bean
    fun springDomainEventListenerRegistrationMachine(
        localDomainEventBus: LocalDomainEventBus,
        domainEventListeners: List<DomainEventListener<*>>,
    ): SpringDomainEventListenerRegistrationMachine {
        return SpringDomainEventListenerRegistrationMachine(
            localDomainEventBus,
            domainEventListeners,
        )
    }
}
