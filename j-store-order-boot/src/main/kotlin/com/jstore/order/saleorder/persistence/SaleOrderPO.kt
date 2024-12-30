package com.jstore.com.jstore.order.saleorder.persistence

import com.jstore.common.persistent.jpa.hibernate.SnowFlakeId
import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(
    name = "sale_order",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sale_order_id", columnNames = ["sale_order_id"]),
    ],
    indexes = [
        Index(name = "idx_uid_create_time_update_time", columnList = "uid, create_time, update_time")
    ]
)

open class SaleOrderPO : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    @Id
    @SnowFlakeId
    @Column(name = "sale_order_id", unique = true, nullable = false, updatable = false)
    open var saleOrderId: Long = 0
    open var uid: Long = 0
    open var phoneNumber: String = ""
    open var userName: String = ""
    open var districtCode: String = ""
    open var detailAddress: String = ""
    open var freightBillId: String = "[]"
    open var positiveStatus: String = ""
    open var reverseStatus: String = ""
    open var amount: BigDecimal = BigDecimal.ZERO
    open var actualPay: BigDecimal = BigDecimal.ZERO
    @CreatedDate
    @Column(name = "create_time", updatable = false)
    open var createTime: LocalDateTime? = null
    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    open var updateTime: LocalDateTime? = null
}
