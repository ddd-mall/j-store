package com.jstore.warehouse.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.warehouse.domain.PhysicalStockId
import com.jstore.warehouse.domain.PhysicalStockRepository
import com.jstore.warehouse.domain.WarehouseErrors

interface WarehouseStockUseCase {
    fun adjust(stockId: PhysicalStockId, quantity: Int, reason: String): Result<Unit, BusinessError>
}

class WarehouseStockService(
    private val stocks: PhysicalStockRepository,
    private val publisher: DomainEventPublisher,
) : WarehouseStockUseCase {
    override fun adjust(
        stockId: PhysicalStockId,
        quantity: Int,
        reason: String,
    ): Result<Unit, BusinessError> {
        val stock = stocks.findById(stockId) ?: return Failure(WarehouseErrors.NOT_FOUND)
        stock.adjustTo(quantity, reason).onFailure {
            return Failure(it)
        }
        stocks.save(stock)
        stock.publishPendingEvents(publisher)
        return Success(Unit)
    }
}
