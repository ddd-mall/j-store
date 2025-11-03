package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.LocalDateTime

/**
 * SKU（Stock Keeping Unit）持久化对象
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "sku",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_sku_id", columnNames = ["sku_id"])
    ],
    indexes = [
        Index(name = "idx_spu_id", columnList = "spu_id"),
        Index(name = "idx_commodity_code", columnList = "commodity_code")
    ]
)
class SkuPO(
    @Id
    @Column(name = "sku_id", unique = true, nullable = false, updatable = false)
    var skuId: Long = 0,

    @Column(name = "spu_id", nullable = false)
    var spuId: Long = 0,

    @Column(name = "commodity_code", nullable = false)
    var commodityCode: Long = 0,

    @Column(name = "attributes", columnDefinition = "TEXT")
    var attributes: String? = null,  // JSON格式存储属性

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

