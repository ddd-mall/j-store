package com.jstore.com.jstore.order.domain.saleorder.persistence

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "sale_order",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sale_order_id", columnNames = ["sale_order_id"]),
    ],
    indexes = [
        Index(name = "idx_uid_create_time_update_time", columnList = "uid, create_time, update_time")
    ]
)

class SaleOrderPO(
    @Id
    @Column(name = "sale_order_id", unique = true, nullable = false, updatable = false)
    val saleOrderId: Long,
    val uid: Long,
    val phoneNumber: String,
    val userName: String,
    val districtCode: String,
    val detailAddress: String,
    val positiveStatus: String,
    val amount: BigDecimal = BigDecimal.ZERO,
    val actualPay: BigDecimal = BigDecimal.ZERO,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1
    }

    @CreatedDate
    @Column(name = "create_time", updatable = false, insertable = true)
    lateinit var createTime: LocalDateTime

    @LastModifiedDate
    @Column(name = "update_time", nullable = false, updatable = true, insertable = true)
    lateinit var updateTime: LocalDateTime
}
