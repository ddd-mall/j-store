package com.jstore.goods.domain.storage

import com.jstore.common.framework.Repository

interface ReservationRecordRepository: Repository<ReservationId ,ReservationRecord> {
    fun findByBizCode(bizCode: String): ReservationRecord?
}