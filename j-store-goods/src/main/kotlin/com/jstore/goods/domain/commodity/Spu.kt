package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.AgreeGate
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Id
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.goods.domain.commodity.event.CommodityOffSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityOnSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent
import java.util.*


class SpuId(override val value: Long) : Id<Long>(value)

interface Spu : AgreeGate<SpuId> {
    val name: String
    val status: CommodityStatus
    val skus: List<Sku>

    /** 增加SKU */
    fun addSku(sku: Sku): Result<Boolean, BusinessError>

    /** 开始售卖 */
    fun putOnSale(): Result<Boolean, BusinessError>

    /** 停止售卖 */
    fun tackOffSale(): Result<Boolean, BusinessError>

    /** 发布 */
    fun publish(): Result<Boolean, BusinessError>
}

class SpuImpl(
    override val id: SpuId,
    override val name: String,
    private var _status: CommodityStatus,
    private val _skus: MutableList<Sku>,
) : Spu {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override val status: CommodityStatus get() = _status
    override val skus: List<Sku> get() = _skus.toList()

    override fun addSku(sku: Sku): Result<Boolean, BusinessError> {
        _skus.add(sku)
        return Success(true)
    }

    override fun putOnSale(): Result<Boolean, BusinessError> {
        if (CommodityStatus.ON_SALE == _status) {
            return Success(true)
        }
        if (CommodityStatus.DRAFT == _status) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("current commodity is in draft, can not operate!!"))
        }
        _status = CommodityStatus.ON_SALE
        publishEvent(CommodityOnSaleEvent(source = this, spuId = id))
        return Success(true)
    }

    override fun tackOffSale(): Result<Boolean, BusinessError> {
        if (CommodityStatus.OFF_SALE == _status) {
            return Success(true)
        }
        if (CommodityStatus.DRAFT == _status) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("current commodity is in draft, can not operate!!"))
        }
        _status = CommodityStatus.OFF_SALE
        publishEvent(CommodityOffSaleEvent(source = this, spuId = id))
        return Success(true)
    }

    override fun publish(): Result<Boolean, BusinessError> {
        if (CommodityStatus.ON_SALE == _status || CommodityStatus.OFF_SALE == _status) {
            return Success(true)
        }
        verifyBeforePublish().onFailure { return Failure(it) }
        _status = CommodityStatus.OFF_SALE
        publishEvent(CommodityPublishedEvent(source = this, spuId = id))
        return Success(true)
    }

    private fun verifyBeforePublish(): Result<Boolean, BusinessError> {
        return Success(true)
    }
}
