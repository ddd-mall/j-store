package com.jstore.goods.domain.commodity


import com.jstore.common.errors.BusinessError
import com.jstore.common.errors.CommonBusinessError
import com.jstore.common.framework.DomainEvent
import com.jstore.common.framework.DomainEventPublisher
import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.utils.*
import com.jstore.goods.domain.commodity.event.CommodityOffSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityOnSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent


class SpuId(override val value: Long) : Id<Long>(value)

interface Spu : Entity<SpuId> {
    /**
     * 增加SKU
     */
    fun addSku(sku: Sku): Result<Boolean, BusinessError>

    /**
     * 开始售卖
     */
    fun putOnSale(): Result<Boolean, BusinessError>

    /**
     * 停止售卖
     */
    fun tackOffSale(): Result<Boolean, BusinessError>

    /**
     * 发布
     */
    fun publish(): Result<Boolean, BusinessError>
}

class SpuImpl(
    override val id: SpuId,
    var status: CommodityStatus,
    val name: String,
    val skus: MutableList<Sku>,
    private val domainEventPublisher: DomainEventPublisher,
) : Spu {

    private val eventBuffer = DomainEventBuffer()

    override fun addSku(sku: Sku): Result<Boolean, BusinessError> {
        skus.add(sku)
        return Success(true)
    }

    override fun putOnSale(): Result<Boolean, BusinessError> {
        if (CommodityStatus.ON_SALE == status) {
            return Success(true)
        }
        if (CommodityStatus.DRAFT == status) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("current commodity is in draft, can not operate!!"))
        }
        this.status = CommodityStatus.ON_SALE
        domainEventPublisher.publishEvent(CommodityOnSaleEvent(this, this.id))
        return Success(true)
    }

    override fun tackOffSale(): Result<Boolean, BusinessError> {
        if (CommodityStatus.OFF_SALE == status) {
            return Success(true)
        }
        if (CommodityStatus.DRAFT == status) {
            return Failure(CommonBusinessError.ILLEGAL_STATE.msg("current commodity is in draft, can not operate!!"))
        }

        this.status = CommodityStatus.OFF_SALE
        domainEventPublisher.publishEvent(CommodityOffSaleEvent(this, this.id))
        return Success(true)
    }

    override fun publish(): Result<Boolean, BusinessError> {
        if (CommodityStatus.ON_SALE == status || CommodityStatus.OFF_SALE == status) {
            return Success(true)
        }
        verifyBeforePublish().onFailure { return Failure(it) }
        this.status = CommodityStatus.OFF_SALE
        domainEventPublisher.publishEvent(CommodityPublishedEvent(this, id))
        return Success(true)
    }

    private fun verifyBeforePublish(): Result<Boolean, BusinessError> {
        return Success(true)
    }

}







