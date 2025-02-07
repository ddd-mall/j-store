package com.jstore.order.domain.saleorder


import com.jstore.common.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.domain.saleorder.properties.GeoAddressInfo
import com.jstore.order.domain.saleorder.properties.UserInfo
import java.math.BigDecimal
import java.time.LocalDateTime


data class SaleOrderId(override val value: Long) : Id<Long>(value)

data class SaleOrder(
    private val id: SaleOrderId,
    val buyerInfo: UserInfo,
    val orderItems: List<OrderItem>,
    var deliveryAddressInfo: GeoAddressInfo,
    var positiveStatus: OrderPositiveStatus,
    var reverseStatus: OrderReverseStatus,
    var amount: Price,
    var actualPay: Price,
    val createTime: LocalDateTime,
    val updateTime: LocalDateTime,
    val type: OrderType = OrderType.NORMAL
) : Entity<SaleOrderId> {


    override fun id(): SaleOrderId {
        return id
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

enum class OrderType {
    NORMAL,
    GROUP,
    SEC_KILL,
    PRE_SELL
}


data class OrderItemId(override val value: Long) : Id<Long>(value)
data class OrderItem(
    val id: OrderItemId,
    val spuId: Long,
    val skuId: Long,
    val goodsVersion: Long,
    val quantity: BigDecimal,
    val unitPrice: Price,
    val totalPrice: Price,
)




