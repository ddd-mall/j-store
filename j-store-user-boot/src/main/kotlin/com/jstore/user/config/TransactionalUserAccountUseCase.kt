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
package com.jstore.user.config

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.Nickname
import com.jstore.user.domain.useraccount.PhoneVerificationProof
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.service.RefreshTokenDigest
import com.jstore.user.service.UserAccountUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/**
 * 用户用例的部署层事务装饰器。
 *
 * 数据库与 Outbox 在事务内完成；Redis token 变更只在事务成功提交后执行。
 */
class TransactionalUserAccountUseCase(
    private val delegate: UserAccountUseCase,
    private val tokenProvider: TokenProvider,
    private val tokenStore: TokenStore,
    transactionManager: PlatformTransactionManager,
) : UserAccountUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun requestPhoneVerification(phoneNumber: PhoneNumber) =
        delegate.requestPhoneVerification(phoneNumber)

    override fun register(cmd: UserRegisterCMD, verificationProof: PhoneVerificationProof) = tx {
        delegate.register(cmd, verificationProof)
    }

    override fun login(phoneNumber: PhoneNumber, rawPassword: String) =
        tx { delegate.login(phoneNumber, rawPassword) }
            .also { result ->
                if (result is Success) {
                    tokenProvider.parseRefreshToken(result.value.refreshToken)?.let { claims ->
                        tokenStore.storeRefreshSession(
                            userId = claims.userId,
                            sessionId = claims.sessionId,
                            refreshTokenDigest =
                                RefreshTokenDigest.sha256(result.value.refreshToken),
                            sessionEpoch = claims.sessionEpoch,
                            ttlSeconds = REFRESH_TOKEN_TTL_SECONDS,
                        )
                    }
                }
            }

    // 刷新令牌是 Redis 主导的外部状态交换，不伪装成数据库原子事务。
    override fun refreshToken(refreshToken: String) = delegate.refreshToken(refreshToken)

    override fun logout(userId: UserId, accessToken: String) = delegate.logout(userId, accessToken)

    override fun findById(userId: UserId) = query { delegate.findById(userId) }

    override fun changeNickname(userId: UserId, newNickname: Nickname) = tx {
        delegate.changeNickname(userId, newNickname)
    }

    override fun changePassword(userId: UserId, oldPassword: String, newPassword: String) =
        txAndRevokeAllSessions(userId) {
            delegate.changePassword(userId, oldPassword, newPassword)
        }

    override fun disable(userId: UserId) =
        txAndRevokeAllSessions(userId) { delegate.disable(userId) }

    override fun enable(userId: UserId) = tx { delegate.enable(userId) }

    override fun forceOffline(userId: UserId) =
        txAndRevokeAllSessions(userId) { delegate.forceOffline(userId) }

    /**
     * Revocation happens before the database commit so a Redis failure rolls back the account
     * change. If the later database commit fails, the conservative result is an extra logout.
     */
    private fun <T> txAndRevokeAllSessions(
        userId: UserId,
        block: () -> Result<T, BusinessError>,
    ): Result<T, BusinessError> = tx {
        block().also { result ->
            if (result is Success) tokenStore.revokeAllSessions(userId)
        }
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })

    private companion object {
        const val REFRESH_TOKEN_TTL_SECONDS = 604800L
    }
}
