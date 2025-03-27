package com.jstore.goods.domain.inventory

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReservationRecord(
    override val id: ReservationId,
    val bizCode: String,
    val commodityCode: CommodityCode,
    val amount: BigDecimal,
    var status: ReservationStatus,
    val expiryTime: LocalDateTime,
) : Entity<ReservationId>

data class ReservationId(override val value: Long) : Id<Long>(value)

enum class ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED
}

