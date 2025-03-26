package com.jstore.goods.domain.storage

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import java.math.BigDecimal
import java.time.LocalDateTime


interface ReservationRecord : Entity<ReservationId> {
    val commodityCode: CommodityCode
    val amount: BigDecimal
    var status: ReservationStatus
    val expiryTime: LocalDateTime

}

data class ReservationId(override val value: Long) : Id<Long>(value)

enum class ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}

