package com.jstore.goods.domain.inventory.persistence

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * 商品库存持久化对象
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "inventory",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_commodity_code", columnNames = ["commodity_code"])
    ],
    indexes = [
        Index(name = "idx_commodity_code", columnList = "commodity_code")
    ]
)
class InventoryPO(
    @Id
    @Column(name = "commodity_code", unique = true, nullable = false, updatable = false)
    var commodityCode: Long = 0,

    @Column(name = "available_quantity", nullable = false, precision = 19, scale = 2)
    var availableQuantity: BigDecimal = BigDecimal.ZERO,

    @Column(name = "reserved_quantity", nullable = false, precision = 19, scale = 2)
    var reservedQuantity: BigDecimal = BigDecimal.ZERO,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0,

    @CreatedDate
    @Column(name = "create_time", updatable = false, insertable = true)
    var createTime: LocalDateTime? = null,

    @LastModifiedDate
    @Column(name = "update_time", nullable = false, updatable = true, insertable = true)
    var updateTime: LocalDateTime? = null
) : Serializable {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

