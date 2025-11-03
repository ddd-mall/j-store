package com.jstore.goods.domain.commodity.persistence

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.io.Serializable
import java.time.LocalDateTime

/**
 * SPU（Standard Product Unit）持久化对象
 */
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "spu",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_spu_id", columnNames = ["spu_id"])
    ],
    indexes = [
        Index(name = "idx_spu_name_status", columnList = "spu_name, status"),
        Index(name = "idx_status", columnList = "status")
    ]
)
class SpuPO(
    @Id
    @Column(name = "spu_id", unique = true, nullable = false, updatable = false)
    var spuId: Long = 0,

    @Column(name = "spu_name", nullable = false, length = 200)
    var spuName: String = "",

    @Column(name = "status", nullable = false, length = 50)
    var status: String = "",

    @Column(name = "description", length = 1000)
    var description: String? = null,

    @Column(name = "category", length = 100)
    var category: String? = null,

    @Column(name = "brand", length = 100)
    var brand: String? = null,

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

