package com.jstore.order.domain.order

import com.jstore.common.framework.Entity
import com.jstore.common.properties.Price

/** 订单行项实体接口 生命周期依附于 Order 聚合根，通过 ID 引用商品（跨聚合引用规则） */
interface OrderItem : Entity<OrderItemId> {
    val offerId: Long
    val storeId: Long
    val offerVersion: Long
    val fulfillmentNodeId: String
    val channelId: String
    val skuId: Long
    val spuId: Long
    val goodsName: String
    val skuDescription: String
    val quantity: Int
    val unitPrice: Price
    val snapshotVersion: Long
    val status: OrderItemStatus

    val purchasedAmount: Price
    val refundedQuantity: Int
    val refundedAmount: Price
    val refundableQuantity: Int
    val refundableAmount: Price

    /** 计算行项小计 */
    fun subtotal(): Price
}
