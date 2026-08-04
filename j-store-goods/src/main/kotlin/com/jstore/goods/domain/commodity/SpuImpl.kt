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
    override val merchantId: MerchantId = MerchantId(1),
    name: String,
    description: String = "",
    private var _status: CommodityStatus,
    private val _skus: MutableList<Sku>,
    private var _version: Long = 1L,
    override val sourceSpuId: SpuId? = null,
) : Spu {

    private var _name: String = name
    private var _description: String = description

    override val domainEventQueue: Queue<DomainEvent> = LinkedList()

    override val name: String get() = _name

    override val description: String get() = _description

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
        _version++
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

    /**
     * 将草稿副本的内容合并到当前 SPU（领域方法）
     * 前置条件：当前 SPU 必须是 ON_SALE 状态，草稿 SKU 列表不能为空
     */
    override fun mergeFromDraft(draft: Spu): Result<Unit, BusinessError> {
        if (draft.merchantId != merchantId) {
            return Failure(CommodityErrors.INVALID_STATUS_TRANSITION.msg("不能合并其他商户的商品草稿"))
        }
        if (_status != CommodityStatus.ON_SALE) {
            return Failure(
                CommodityErrors.INVALID_STATUS_TRANSITION
                    .msg("只有在售商品可以合并草稿，当前状态: $_status")
            )
        }
        if (draft.skus.isEmpty()) {
            return Failure(CommodityErrors.DRAFT_NO_SKU_FOR_PUBLISH)
        }
        _name = draft.name
        _description = draft.description
        _skus.clear()
        _skus.addAll(draft.skus)
        _version++
        return Success(Unit)
    }
}
