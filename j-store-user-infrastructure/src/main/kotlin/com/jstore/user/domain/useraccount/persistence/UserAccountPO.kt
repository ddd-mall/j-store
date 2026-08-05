package com.jstore.user.domain.useraccount.persistence

import com.jstore.user.domain.useraccount.UserAccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_accounts")
class UserAccountPO(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") var id: Long = 0,
    @Column(name = "phone_number", nullable = false, unique = true, length = 11)
    var phoneNumber: String = "",
    @Column(name = "nickname", nullable = false, length = 20) var nickname: String = "",
    @Column(name = "password_hash", nullable = false, length = 255) var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: UserAccountStatus = UserAccountStatus.ACTIVE,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
