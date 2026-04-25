package com.jstore.order.domain.order

import com.jstore.common.properties.Price

/**
 * 订单行项实体实现
 */
class OrderItemImpl(
    override val id: OrderItemId,
    override val skuId: Long,
    override val spuId: Long,
    override val goodsName: String,
    override val skuDescription: String,
    override val quantity: Int,
    override val unitPrice: Price,
    override var status: OrderItemStatus = OrderItemStatus.NONE,
    private var _previousItemStatus: OrderItemStatus? = null,
) : OrderItem {

    override val previousItemStatus: OrderItemStatus? get() = _previousItemStatus

    init {
        require(quantity > 0) { "商品数量必须大于0" }
    }

    override fun subtotal(): Price = unitPrice * quantity

    /** 进入退款状态，记录当前状态以便恢复 */
    fun enterRefunding() {
        _previousItemStatus = status
        status = OrderItemStatus.REFUNDING
    }

    /** 退款被批准，进入 CANCELED */
    fun markCanceled() {
        _previousItemStatus = null
        status = OrderItemStatus.CANCELED
    }

    /** 退款被拒绝，恢复到进入退款前的状态 */
    fun restoreFromRefunding() {
        val restoreTo = _previousItemStatus
            ?: throw IllegalStateException("previousItemStatus 为空，无法恢复")
        status = restoreTo
        _previousItemStatus = null
    }
}
