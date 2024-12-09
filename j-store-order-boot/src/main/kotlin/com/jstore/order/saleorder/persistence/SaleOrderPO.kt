package com.jstore.com.jstore.order.saleorder.persistence

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import jakarta.persistence.*
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "sale_order",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_id", columnNames = ["id"]),
        UniqueConstraint(name = "uk_sale_order_id", columnNames = ["sale_order_id"]),
    ],
    indexes = [
        Index(name = "idx_uid_create_time_update_time", columnList = "uid, create_time, update_time")
    ]
)
@IdClass(SaleOrderIdClass::class)
open class SaleOrderPO {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", unique = true, nullable = false, updatable = false)
    open var id: Long? = null

    @Id
    @SnowFlakeId
    @Column(name = "sale_order_id", unique = true, nullable = false, updatable = false)
    open var saleOrderId: Long? = null
    open var uid: Long? = null
    open var phoneNumber: String? = null
    open var userName: String? = null
    open var districtCode: String? = null
    open var detailAddress: String? = null
    open var freightBillId: String? = null
    open var positiveStatus: String? = null
    open var reverseStatus: String? = null
    open var amount: BigDecimal? = null
    open var actualPay: BigDecimal? = null
    open var createTime: LocalDateTime? = null
    open var updateTime: LocalDateTime? = null
}

open class SaleOrderIdClass : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }
    private val id: Long? = null
    private val saleOrderId: Long? = null


}
