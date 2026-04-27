package com.jstore.goods.domain.commodity

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.goods.domain.commodity.event.CommodityOffSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityOnSaleEvent
import com.jstore.goods.domain.commodity.event.CommodityPublishedEvent
import java.util.*

class SpuImpl(
    override val id: SpuId,
    override val name: String,
    override val description: String = "",
    private var _status: CommodityStatus,
    private val _skus: MutableList<Sku>,
    private var _version: Long = 1L,
) : Spu {

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override val skus: List<Sku> get() = _skus.toList()

    override val status: CommodityStatus get() = _status

    override val version: Long get() = _version

    override fun addSku(sku: Sku): Result<Unit, BusinessError> {
        // 检查属性组合是否重复
        val newKey = sku.attributes.map { "${it.key}:${it.value}" }.sorted()
        val duplicate = _skus.any { existing ->
            existing.attributes.map { "${it.key}:${it.value}" }.sorted() == newKey
        }
        if (duplicate) {
            return Failure(CommodityErrors.DUPLICATE_SKU_ATTRIBUTES)
        }
        _skus.add(sku)
        return Success(Unit)
    }

    override fun publish(): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.DRAFT) {
            return Failure(CommodityErrors.INVALID_STATUS_TRANSITION.msg("只有草稿状态可以发布，当前状态: $_status"))
        }
        if (_skus.isEmpty()) {
            return Failure(CommodityErrors.NO_SKU_FOR_PUBLISH)
        }
        _status = CommodityStatus.OFF_SALE
        publishEvent(CommodityPublishedEvent(source = this, spuId = id))
        return Success(Unit)
    }

    override fun putOnSale(): Result<Unit, BusinessError> {
        if (_status == CommodityStatus.DRAFT) {
            return Failure(CommodityErrors.DRAFT_CANNOT_ON_SALE)
        }
        if (_status == CommodityStatus.ON_SALE) {
            return Failure(CommodityErrors.ALREADY_ON_SALE)
        }
        _status = CommodityStatus.ON_SALE
        publishEvent(CommodityOnSaleEvent(source = this, spuId = id, snapshotVersion = _version))
        return Success(Unit)
    }

    override fun takeOffSale(): Result<Unit, BusinessError> {
        if (_status != CommodityStatus.ON_SALE) {
            return Failure(CommodityErrors.ALREADY_OFF_SALE.msg("只有在售商品可以下架，当前状态: $_status"))
        }
        _status = CommodityStatus.OFF_SALE
        publishEvent(CommodityOffSaleEvent(source = this, spuId = id))
        return Success(Unit)
    }

    override fun incrementVersion(): Long {
        _version++
        return _version
    }
}
