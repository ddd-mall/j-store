package com.jstore.goods.domain.storage

import java.math.BigDecimal

interface ReservationRecordFactory {
    fun create(bizCode: String, commodityCode: CommodityCode, amount: BigDecimal): ReservationRecord
}