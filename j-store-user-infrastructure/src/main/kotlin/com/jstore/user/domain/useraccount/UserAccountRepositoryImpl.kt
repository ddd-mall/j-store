package com.jstore.user.domain.useraccount

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.persistence.UserAccountPO
import com.jstore.user.domain.useraccount.persistence.UserAccountPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class UserAccountRepositoryImpl(private val jpaRepository: UserAccountPOJpaRepository) :
    UserAccountRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun add(userAccount: UserAccount) {
        val po = Converter.toPO(userAccount)
        jpaRepository.save(po)
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: UserAccount): UserAccount {
        val po = Converter.toPO(entity)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: UserId): UserAccount? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findByPhoneNumber(phoneNumber: PhoneNumber): UserAccount? {
        return jpaRepository.findByPhoneNumber(phoneNumber.value)?.let { Converter.toDomain(it) }
    }

    override fun existsById(id: UserId): Boolean {
        return jpaRepository.existsById(id.value)
    }

    override fun existsByPhoneNumber(phoneNumber: PhoneNumber): Boolean {
        return jpaRepository.existsByPhoneNumber(phoneNumber.value)
    }

    private object Converter {

        fun toPO(userAccount: UserAccount): UserAccountPO {
            return UserAccountPO(
                id = userAccount.id.value,
                phoneNumber = userAccount.phoneNumber.value,
                nickname = userAccount.nickname.value,
                passwordHash = userAccount.passwordHash.hashedValue,
                status = userAccount.status,
                createTime = userAccount.createTime,
                updateTime = userAccount.updateTime,
            )
        }

        fun toDomain(po: UserAccountPO): UserAccount {
            return UserAccountImpl(
                id = UserId(po.id),
                phoneNumber = PhoneNumber(po.phoneNumber),
                nickname = Nickname(po.nickname),
                passwordHash = Password(po.passwordHash),
                status = po.status,
                createTime = po.createTime,
                updateTime = po.updateTime,
            )
        }
    }
}
