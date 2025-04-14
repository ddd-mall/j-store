package com.jstore.order.domain.saleorder


import com.jstore.common.framework.AggreGate
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.domain.saleorder.properties.GeoAddressInfo
import com.jstore.order.domain.saleorder.properties.UserInfo
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.LinkedBlockingQueue


data class SaleOrderId(override val value: Long) : Id<Long>(value)

class SaleOrder : AggreGate<SaleOrderId> {
    override val domainEventQueue: Queue<DomainEvent> = LinkedBlockingQueue()

    override val id: SaleOrderId
    val buyerInfo: UserInfo
    val orderItems: List<OrderItem>
    var deliveryAddressInfo: GeoAddressInfo
    var positiveStatus: OrderPositiveStatus
    var reverseStatus: OrderReverseStatus
    var amount: Price
    var actualPay: Price
    val createTime: LocalDateTime
    val updateTime: LocalDateTime

    constructor(
        id: SaleOrderId,
        buyerInfo: UserInfo,
        orderItems: List<OrderItem>,
        deliveryAddressInfo: GeoAddressInfo,
        positiveStatus: OrderPositiveStatus,
        reverseStatus: OrderReverseStatus,
        amount: Price,
        actualPay: Price,
        createTime: LocalDateTime,
        updateTime: LocalDateTime,
    ) {
        this.id = id
        this.buyerInfo = buyerInfo
        this.orderItems = orderItems
        this.deliveryAddressInfo = deliveryAddressInfo
        this.positiveStatus = positiveStatus
        this.reverseStatus = reverseStatus
        this.amount = amount
        this.actualPay = actualPay
        this.createTime = createTime
        this.updateTime = updateTime
    }



}


enum class OrderPositiveStatus {
    WAIT_PAY,
    WAIT_FOR_SELLER_DELIVERY,
    WAIT_FOR_BUYER_RECEIPT,
    COMPLETE,
}

enum class OrderReverseStatus {
    NONE,
    REFUNDING,
    WAIT_FOR_BUYER_DELIVERY,
    WAIT_FOR_SELLER_RECEIPT,
    CANCELED,
    CLOSE
}


