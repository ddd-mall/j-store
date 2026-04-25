package com.jstore.order.domain.order

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Price

/**
 * 订单行项实体接口
 * 生命周期依附于 Order 聚合根，通过 ID 引用商品（跨聚合引用规则）
 */
interface OrderItem : Entity<OrderItemId> {
    val skuId: Long
    val spuId: Long
    val goodsName: String
    val skuDescription: String
    val quantity: Int
    val unitPrice: Price
    val status: OrderItemStatus

    /** 进入 REFUNDING 前的行项状态，用于退款拒绝时恢复 */
    val previousItemStatus: OrderItemStatus?

    /** 计算行项小计 */
    fun subtotal(): Price
}
