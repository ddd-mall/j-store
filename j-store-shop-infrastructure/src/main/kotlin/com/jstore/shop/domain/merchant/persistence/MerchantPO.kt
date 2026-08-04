package com.jstore.shop.domain.merchant.persistence

import com.jstore.shop.domain.merchant.MerchantStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "merchants")
class MerchantPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "name", nullable = false, length = 128) var name: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: MerchantStatus = MerchantStatus.ACTIVE,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
