package com.jstore.shop.domain.offer

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.AggregateRepository
import com.jstore.common.framework.AggregateRoot
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success

enum class StoreStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED,
}

class Store(
    override val id: StoreId,
    val merchantId: MerchantId,
    val name: String,
    status: StoreStatus,
    val persistenceVersion: Long = 0,
) : AggregateRoot<StoreId> {
    private var _status = status

    val status: StoreStatus
        get() = _status

    init {
        require(name.isNotBlank())
    }

    fun suspend(): Result<Unit, BusinessError> {
        if (_status != StoreStatus.ACTIVE) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = StoreStatus.SUSPENDED
        return Success(Unit)
    }

    fun activate(): Result<Unit, BusinessError> {
        if (_status != StoreStatus.SUSPENDED) return Failure(OfferErrors.ILLEGAL_STATE)
        _status = StoreStatus.ACTIVE
        return Success(Unit)
    }
}

interface StoreRepository : AggregateRepository<StoreId, Store>

fun interface StoreGuard {
    fun lock(ids: List<StoreId>): List<Store>
}
