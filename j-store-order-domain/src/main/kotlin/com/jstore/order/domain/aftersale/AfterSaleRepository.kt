package com.jstore.order.domain.aftersale

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.utils.Result
import com.jstore.order.domain.order.OrderId

interface AfterSaleRepository : AggregateRepository<AfterSaleId, AfterSale> {
    fun createWithAllocation(
        afterSale: AfterSale,
        ceilings: List<RefundCapacityCeiling>,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError>

    fun findByOrderId(orderId: OrderId): List<AfterSale>

    fun saveDecision(
        afterSale: AfterSale,
        allocationAction: AllocationAction,
        receipt: AfterSaleCommandReceipt,
    ): Result<AfterSale, BusinessError>

    fun findReceipt(
        actorId: Long,
        type: AfterSaleCommandType,
        key: String,
    ): AfterSaleCommandReceipt?
}
