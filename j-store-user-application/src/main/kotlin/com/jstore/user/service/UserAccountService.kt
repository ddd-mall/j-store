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
package com.jstore.user.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.domain.useraccount.event.UserAccountForcedOfflineEvent
import com.jstore.user.domain.useraccount.event.UserAccountLoggedInEvent
import java.time.LocalDateTime

/** 用户账号应用服务 编排用例: 加载聚合 → 执行领域行为 → 保存 → 发布事件 不包含业务规则，全部委托给领域对象 */
class UserAccountService(
    private val userAccountFactory: UserAccountFactory,
    private val userAccountRepository: UserAccountRepository,
    private val passwordHasher: PasswordHasher,
    private val tokenProvider: TokenProvider,
    private val tokenStore: TokenStore,
    private val domainEventPublisher: DomainEventPublisher,
) : UserAccountUseCase {

    companion object {
        private const val REFRESH_TOKEN_TTL_SECONDS = 604800L // 7 days
    }

    /** 用户注册 */
    override fun register(cmd: UserRegisterCMD): Result<UserAccount, BusinessError> {
        if (userAccountRepository.existsByPhoneNumber(cmd.phoneNumber)) {
            return Failure(UserAccountErrors.PHONE_ALREADY_REGISTERED)
        }
        val account =
            userAccountFactory.create(cmd, passwordHasher).onFailure {
                return Failure(it)
            } as Success
        userAccountRepository.add(account.value)
        account.value.publishPendingEvents(domainEventPublisher)
        return account
    }

    /** 用户登录 */
    override fun login(
        phoneNumber: PhoneNumber,
        rawPassword: String,
    ): Result<AuthTokenPair, BusinessError> {
        val account =
            userAccountRepository.findByPhoneNumber(phoneNumber)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)

        if (!passwordHasher.matches(rawPassword, account.passwordHash.hashedValue)) {
            return Failure(UserAccountErrors.PASSWORD_MISMATCH)
        }

        if (account.status != UserAccountStatus.ACTIVE) {
            return Failure(UserAccountErrors.ACCOUNT_DISABLED)
        }

        val accessToken = tokenProvider.issueAccessToken(account.id)
        val refreshToken = tokenProvider.issueRefreshToken(account.id)
        val tokenPair =
            AuthTokenPair(
                accessToken = accessToken,
                accessTokenExpiresAt = LocalDateTime.now().plusMinutes(15),
                refreshToken = refreshToken,
                refreshTokenExpiresAt = LocalDateTime.now().plusDays(7),
            )

        domainEventPublisher.publishEvent(
            UserAccountLoggedInEvent(
                userId = account.id,
                loginTime = LocalDateTime.now(),
            )
        )

        return Success(tokenPair)
    }

    /** Token 刷新 */
    override fun refreshToken(refreshToken: String): Result<AuthTokenPair, BusinessError> {
        val userId =
            tokenProvider.parseRefreshToken(refreshToken)
                ?: return Failure(UserAccountErrors.TOKEN_INVALID)

        val storedToken = tokenStore.getRefreshToken(userId)
        if (storedToken != refreshToken) {
            tokenStore.removeRefreshToken(userId)
            return Failure(UserAccountErrors.REFRESH_TOKEN_REVOKED)
        }

        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)

        if (account.status != UserAccountStatus.ACTIVE) {
            tokenStore.removeRefreshToken(userId)
            return Failure(UserAccountErrors.ACCOUNT_DISABLED)
        }

        val newAccessToken = tokenProvider.issueAccessToken(userId)
        val newRefreshToken = tokenProvider.issueRefreshToken(userId)
        tokenStore.storeRefreshToken(userId, newRefreshToken, REFRESH_TOKEN_TTL_SECONDS)

        return Success(
            AuthTokenPair(
                accessToken = newAccessToken,
                accessTokenExpiresAt = LocalDateTime.now().plusMinutes(15),
                refreshToken = newRefreshToken,
                refreshTokenExpiresAt = LocalDateTime.now().plusDays(7),
            )
        )
    }

    /** 根据 ID 查询用户 */
    override fun findById(userId: UserId): Result<UserAccount, BusinessError> {
        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)
        return Success(account)
    }

    /** 修改昵称 */
    override fun changeNickname(
        userId: UserId,
        newNickname: Nickname,
    ): Result<Unit, BusinessError> {
        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)
        account.changeNickname(newNickname).onFailure {
            return Failure(it)
        }
        userAccountRepository.save(account)
        account.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 修改密码 */
    override fun changePassword(
        userId: UserId,
        oldPassword: String,
        newPassword: String,
    ): Result<Unit, BusinessError> {
        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)

        if (!passwordHasher.matches(oldPassword, account.passwordHash.hashedValue)) {
            return Failure(UserAccountErrors.OLD_PASSWORD_MISMATCH)
        }

        if (!UserAccountFactoryImpl.validatePasswordStrength(newPassword)) {
            return Failure(UserAccountErrors.PASSWORD_STRENGTH_INSUFFICIENT)
        }

        val newHash = passwordHasher.hash(newPassword)
        account.changePassword(Password(newHash)).onFailure {
            return Failure(it)
        }
        userAccountRepository.save(account)
        account.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 禁用账号（自动执行强制下线） */
    override fun disable(userId: UserId): Result<Unit, BusinessError> {
        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)
        account.disable().onFailure {
            return Failure(it)
        }
        userAccountRepository.save(account)
        account.publishPendingEvents(domainEventPublisher)

        domainEventPublisher.publishEvent(
            UserAccountForcedOfflineEvent(
                userId = userId,
                operationTime = LocalDateTime.now(),
            )
        )

        return Success(Unit)
    }

    /** 启用账号 */
    override fun enable(userId: UserId): Result<Unit, BusinessError> {
        val account =
            userAccountRepository.findById(userId)
                ?: return Failure(UserAccountErrors.USER_NOT_FOUND)
        account.enable().onFailure {
            return Failure(it)
        }
        userAccountRepository.save(account)
        account.publishPendingEvents(domainEventPublisher)
        return Success(Unit)
    }

    /** 强制下线 */
    override fun forceOffline(userId: UserId, accessToken: String?): Result<Unit, BusinessError> {
        userAccountRepository.findById(userId) ?: return Failure(UserAccountErrors.USER_NOT_FOUND)

        domainEventPublisher.publishEvent(
            UserAccountForcedOfflineEvent(
                userId = userId,
                operationTime = LocalDateTime.now(),
            )
        )

        return Success(Unit)
    }
}
