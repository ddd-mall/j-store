package com.jstore.order.saleorder

import com.jstore.com.jstore.framework.Entity
import com.jstore.com.jstore.framework.Identify
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.saleorder.properties.Price
import java.time.LocalDateTime

class SaleOrder(
    private val id: OrderId<Long>,
    private val buyerInfo: UserInfo,
    private val orderItems: List<OrderItem>,
    private val FerightBills: List<FreightBill>,
    private var positiveStatus: OrderPositiveStatus,
    private var reverseStatus: OrderReverseStatus,
    private val amount: Price,
    private var actualPay: Price,
    private val createTime: LocalDateTime,
    private val updateTime: LocalDateTime,
) : Entity<OrderId<Long>> {
    override fun getId(): OrderId<Long> {
        return id;
    }
}


data class OrderId<T>(val value: T) : Identify


enum class OrderPositiveStatus {
    CREATING,
    WAITPAY,
    WAIT_FOR_SALLER_DELIVERY,
    WAIT_FOR_BUYER_RECEIPT,
    COMPLETE,
}

enum class OrderReverseStatus {
    NONE,
    REFUNDING,
    WAIT_FOR_BUYER_DELIVERY,
    WAIT_FOR_SALLER_RECEIPT,
    CANCELED,
    CLOSE
}


data class UserInfo(
    val uid: Long,
    var phoneNumber: PhoneNumber,
    var userName: String
)


data class OrderItem(
    private val id: Long,
    private val spuId: Long,
    private val skuId: Long,
    private val skuVersion: Long,
    private val count: Int,
    private val unitPrice: Price,
    private val totalPrice: Price,
)


data class FreightBill(val id: String)

