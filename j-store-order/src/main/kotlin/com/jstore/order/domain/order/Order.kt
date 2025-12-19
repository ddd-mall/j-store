package com.jstore.order.domain.order

import com.jstore.common.framework.AgreeGate
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.item.OrderItem
import java.time.LocalDateTime

interface Order : AgreeGate<OrderId> {
    override val id: OrderId
    val buyerInfo: UserInfo
    val orderItemImpls: List<OrderItem>
    var shippingAddressInfo: GeoAddressInfo
    var status: OrderStatus
    var amount: Price
    var actualPay: Price
    val createTime: LocalDateTime?
    val updateTime: LocalDateTime?

    fun reserve(): Order

    fun pay(): Order

    fun shipping(): Order

    fun complete(): Order

    fun cancel(): Order

    fun refund(): Order

    fun confirm(): Order

    fun undo(): Order


}