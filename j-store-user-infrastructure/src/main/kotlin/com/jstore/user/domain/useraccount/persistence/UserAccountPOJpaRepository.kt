package com.jstore.user.domain.useraccount.persistence

import org.springframework.data.jpa.repository.JpaRepository

interface UserAccountPOJpaRepository : JpaRepository<UserAccountPO, Long> {

    fun findByPhoneNumber(phoneNumber: String): UserAccountPO?

    fun existsByPhoneNumber(phoneNumber: String): Boolean
}
