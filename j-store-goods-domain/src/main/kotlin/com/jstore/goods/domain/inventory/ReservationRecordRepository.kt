package com.jstore.goods.domain.inventory

import com.jstore.common.framework.AggregateRepository

interface ReservationRecordRepository : AggregateRepository<ReservationId, ReservationRecord> {
    fun findByBizCode(bizCode: String): ReservationRecord?
}
