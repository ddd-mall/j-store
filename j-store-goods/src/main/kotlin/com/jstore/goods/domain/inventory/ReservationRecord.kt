package com.jstore.goods.domain.inventory

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import java.math.BigDecimal
import java.time.LocalDateTime

data class ReservationRecord(
    override val id: ReservationId,
    val bizCode: String,
    val commodityCode: CommodityCode,
    val amount: BigDecimal,
    var status: ReservationStatus,
    val expiryTime: LocalDateTime,
) : Entity<ReservationId> {

    /** 确认扣减：RESERVED → CONFIRMED */
    fun confirm(): Result<Unit, BusinessError> {
        if (status == ReservationStatus.CONFIRMED) return Success(Unit)
        if (status == ReservationStatus.RELEASED || expiryTime < LocalDateTime.now()) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("预扣记录已释放或已过期"))
        }
        status = ReservationStatus.CONFIRMED
        return Success(Unit)
    }

    /** 释放预扣：RESERVED → RELEASED */
    fun release(): Result<Unit, BusinessError> {
        if (status == ReservationStatus.RELEASED) return Success(Unit)
        if (status == ReservationStatus.CONFIRMED) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("预扣记录已确认扣减，无法释放"))
        }
        status = ReservationStatus.RELEASED
        return Success(Unit)
    }
}

data class ReservationId(override val value: Long) : Id<Long>(value)

enum class ReservationStatus {
    RESERVED,
    CONFIRMED,
    RELEASED,
}
