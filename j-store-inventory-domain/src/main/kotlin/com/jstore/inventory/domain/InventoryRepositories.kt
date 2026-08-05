package com.jstore.inventory.domain

import com.jstore.common.framework.AggregateRepository

interface StockPositionRepository : AggregateRepository<StockPositionId, StockPosition> {
    fun findBySkuAndNode(skuId: SkuId, nodeId: FulfillmentNodeId): StockPosition?
}

fun interface StockPositionGuard {
    fun lock(keys: List<StockPositionId>): List<StockPosition>
}

interface StockReservationRepository :
    AggregateRepository<StockReservationId, StockReservation> {
    fun findByBusinessKey(businessKey: String): StockReservation?

    fun findByOrderId(orderId: Long): List<StockReservation>
}
