package com.jstore.order.saleorder

import com.jstore.com.jstore.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.common.properties.Price
import com.jstore.order.saleorder.properties.FreightBill
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.order.saleorder.properties.UserInfo
import java.math.BigDecimal
import java.time.LocalDateTime


data class SaleOrderId(override val value: Long) : Id<Long>(value)

/**
 * 销售单，创建时预扣商品库存。
 *
 */
interface SaleOrder : Entity<SaleOrderId> {
    fun pay()
    fun cancel()
    fun close()
    val buyerInfo: UserInfo
    val orderItems: List<OrderItem>
    var deliveryAddressInfo: GeoAddressInfo
    val freightBills: List<FreightBill>?
    var positiveStatus: OrderPositiveStatus?
    var reverseStatus: OrderReverseStatus?
    var amount: Price
    var actualPay: Price
    val createTime: LocalDateTime?
    val updateTime: LocalDateTime?
}

data class NormalSaleOrderImpl(
    private val id: SaleOrderId?,
    override val buyerInfo: UserInfo,
    override val orderItems: List<OrderItem>,
    override var deliveryAddressInfo: GeoAddressInfo,
    override val freightBills: List<FreightBill>?,
    override var positiveStatus: OrderPositiveStatus? = null,
    override var reverseStatus: OrderReverseStatus? = null,
    override var amount: Price,
    override var actualPay: Price,
    override val createTime: LocalDateTime? = null,
    override val updateTime: LocalDateTime? = null,
    private val type: OrderType = OrderType.NORMAL
) : SaleOrder {

    override fun getId(): SaleOrderId? {
        return id
    }

    override fun pay() {

    }

    override fun cancel() {
        TODO("Not yet implemented")
    }

    override fun close() {
        TODO("Not yet implemented")
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
    val id: OrderItemId? = null,
    val spuId: Long,
    val skuId: Long,
    val skuVersion: Long,
    val count: BigDecimal,
    val unitPrice: Price,
    val totalPrice: Price,
)




