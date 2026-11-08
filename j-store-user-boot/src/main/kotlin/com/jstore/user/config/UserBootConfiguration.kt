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

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.user.domain.useraccount.*
import com.jstore.user.security.RedisLoginAttemptGuard
import com.jstore.user.security.RedisPhoneVerificationGateway
import com.jstore.user.service.UserAccountService
import com.jstore.user.service.UserAccountUseCase
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class UserBootConfiguration {

  @Bean
  fun userAccountFactory(snowFlakSequence: SnowFlakSequence): UserAccountFactory {
    return UserAccountFactoryImpl(snowFlakSequence)
  }

  @Bean
  fun bcryptPasswordHasher(): PasswordHasher {
    return BCryptPasswordHasher()
  }

  @Bean
  fun redisAccountSessionStore(
      stringRedisTemplate: StringRedisTemplate,
      @Value($$"${jstore.oidc.issuer}") issuer: String,
  ): RedisAccountSessionStore {
    return RedisAccountSessionStore(
        stringRedisTemplate,
        com.jstore.user.oidc.DigestAuthorizationService.hash(issuer),
    )
  }

  @Bean
  fun phoneVerificationGateway(
      stringRedisTemplate: StringRedisTemplate,
      @Value($$"${account.phone-verification.hmac-secret}") hmacSecret: String,
  ): PhoneVerificationGateway = RedisPhoneVerificationGateway(stringRedisTemplate, hmacSecret)

  @Bean
  fun loginAttemptGuard(stringRedisTemplate: StringRedisTemplate): LoginAttemptGuard =
      RedisLoginAttemptGuard(stringRedisTemplate)

  @Bean
  fun userAccountService(
      userAccountFactory: UserAccountFactory,
      userAccountRepository: UserAccountRepository,
      passwordHasher: PasswordHasher,
      phoneVerificationGateway: PhoneVerificationGateway,
      phoneVerificationCodeSender: PhoneVerificationCodeSender,
      domainEventPublisher: DomainEventPublisher,
  ): UserAccountService {
    return UserAccountService(
        userAccountFactory = userAccountFactory,
        userAccountRepository = userAccountRepository,
        passwordHasher = passwordHasher,
        phoneVerificationGateway = phoneVerificationGateway,
        phoneVerificationCodeSender = phoneVerificationCodeSender,
        domainEventPublisher = domainEventPublisher,
    )
  }

  @Bean
  @Primary
  fun transactionalUserAccountUseCase(
      userAccountService: UserAccountService,
      tokenStore: AccountSessionStore,
      transactionManager: PlatformTransactionManager,
  ): UserAccountUseCase =
      TransactionalUserAccountUseCase(
          delegate = userAccountService,
          tokenStore = tokenStore,
          transactionManager = transactionManager,
      )
}
