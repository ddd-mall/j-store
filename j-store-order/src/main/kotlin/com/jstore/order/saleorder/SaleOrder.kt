package com.jstore.order.saleorder

import com.jstore.com.jstore.framework.Entity
import com.jstore.order.acl.freight.FreightServiceFactory
import com.jstore.order.refund.RefundType
import com.jstore.order.refund.service.RefundServiceFactory
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.errors.CommonErrors.ILLEGAL_STATE
import com.jstore.common.errors.Errors
import com.jstore.order.common.Id
import com.jstore.order.saleorder.properties.FreightBill
import com.jstore.common.properties.Price
import com.jstore.order.saleorder.properties.UserInfo
import java.time.LocalDateTime


data class SaleOrderId(override val value: Long): Id<Long>(value)
data class SaleOrder(
    private val id: SaleOrderId?,
    val buyerInfo: UserInfo,
    val orderItems: List<OrderItem>?,
    var deliveryAddressInfo: GeoAddressInfo,
    val freightBills: List<FreightBill>?,
    var positiveStatus: OrderPositiveStatus = OrderPositiveStatus.WAIT_PAY,
    var reverseStatus: OrderReverseStatus = OrderReverseStatus.NONE,
    var amount: Price,
    var actualPay: Price,
    val createTime: LocalDateTime? = null,
    val updateTime: LocalDateTime? = null,
) : Entity<SaleOrderId> {

    companion object {
        private val ORDER_DOES_NOT_PERSIST: Errors = ILLEGAL_STATE.withMsg("订单未持久化")
    }

    override fun getId(): SaleOrderId? {
        return id
    }

    /**
     * 售后
     */
    fun refund(
        refundType: RefundType,
        reason: String?
    ) {
        RefundServiceFactory.getOne().createRefund(
            getId()?: throw ORDER_DOES_NOT_PERSIST,
            refundType,
            reason,
            this.actualPay
        )
    }

    /**
     * 发货
     */
    fun delivery() {
        if (this.positiveStatus != OrderPositiveStatus.WAIT_FOR_SELLER_DELIVERY) {
            throw ILLEGAL_STATE.withMsg("当前不允许执行此操作")
        }
        FreightServiceFactory.getAny().delivery(this)
        this.positiveStatus = OrderPositiveStatus.WAIT_FOR_BUYER_RECEIPT
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




data class OrderItemId(override val value: Long): Id<Long>(value)
data class OrderItem(
    val id: OrderItemId? = null,
    val spuId: Long,
    val skuId: Long,
    val skuVersion: Long,
    val count: Int,
    val unitPrice: Price,
    val totalPrice: Price,
)




