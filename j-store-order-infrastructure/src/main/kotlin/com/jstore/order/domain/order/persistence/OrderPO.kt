package com.jstore.order.domain.order.persistence

import jakarta.persistence.*
import lombok.NoArgsConstructor
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
        UniqueConstraint(name = "uk", columnNames = ["order_id"]),
    ],
    indexes = [
        Index(name = "idx_uid_create_time_update_time", columnList = "uid, create_time, update_time")
    ]
)

@NoArgsConstructor
class OrderPO(
    @Id
    @Column(name = "order_id", unique = true, nullable = false, updatable = false)
    var orderId: Long,
    var uid: Long = 0,
    var phoneNumber: String = "",
    var userName: String = "",
    var addressInfo: String = "",
    var positiveStatus: String = "",
    var amount: BigDecimal = BigDecimal.ZERO,
    var actualPay: BigDecimal = BigDecimal.ZERO,
    @CreatedDate
    @Column(name = "create_time", updatable = false, insertable = true)
    var createTime: LocalDateTime? = null,

    @LastModifiedDate
    @Column(name = "update_time", nullable = false, updatable = true, insertable = true)
    var updateTime: LocalDateTime? = null,
) : Serializable {

    constructor() : this(orderId = 0)

    companion object {
        private const val serialVersionUID: Long = 1
    }


}
