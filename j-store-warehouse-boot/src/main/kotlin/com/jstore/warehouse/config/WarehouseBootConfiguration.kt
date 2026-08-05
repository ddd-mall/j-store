package com.jstore.warehouse.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.warehouse.domain.PhysicalStockId
import com.jstore.warehouse.domain.PhysicalStockRepository
import com.jstore.warehouse.service.WarehouseStockService
import com.jstore.warehouse.service.WarehouseStockUseCase
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@Configuration
class WarehouseBootConfiguration {
    @Bean
    fun warehouseStockService(
        stocks: PhysicalStockRepository,
        publisher: DomainEventPublisher,
        transactionManager: PlatformTransactionManager,
    ): WarehouseStockUseCase {
        val delegate = WarehouseStockService(stocks, publisher)
        val transaction = TransactionTemplate(transactionManager)
        return object : WarehouseStockUseCase {
            override fun adjust(
                stockId: PhysicalStockId,
                quantity: Int,
                reason: String,
            ) = requireNotNull(transaction.execute { delegate.adjust(stockId, quantity, reason) })
        }
    }
}
