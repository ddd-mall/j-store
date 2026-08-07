/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.user.domain.useraccount

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.persistence.UserAccountPO
import com.jstore.user.domain.useraccount.persistence.UserAccountPOJpaRepository
import org.springframework.stereotype.Repository

@Repository
class UserAccountRepositoryImpl(private val jpaRepository: UserAccountPOJpaRepository) :
    UserAccountRepository {

    override fun add(userAccount: UserAccount) {
        val po = Converter.toPO(userAccount)
        jpaRepository.save(po)
    }

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
