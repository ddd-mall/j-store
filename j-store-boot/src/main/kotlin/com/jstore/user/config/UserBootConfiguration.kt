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
import com.jstore.user.filter.JwtAuthenticationFilter
import com.jstore.user.service.UserAccountService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.StringRedisTemplate

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
    fun jwtTokenProvider(@Value("\${jwt.secret}") secretKey: String): TokenProvider {
        return JwtTokenProvider(secretKey)
    }

    @Bean
    fun redisTokenStore(stringRedisTemplate: StringRedisTemplate): TokenStore {
        return RedisTokenStore(stringRedisTemplate)
    }

    @Bean
    fun userAccountService(
        userAccountFactory: UserAccountFactory,
        userAccountRepository: UserAccountRepository,
        passwordHasher: PasswordHasher,
        tokenProvider: TokenProvider,
        tokenStore: TokenStore,
        domainEventPublisher: DomainEventPublisher,
    ): UserAccountService {
        return UserAccountService(
            userAccountFactory = userAccountFactory,
            userAccountRepository = userAccountRepository,
            passwordHasher = passwordHasher,
            tokenProvider = tokenProvider,
            tokenStore = tokenStore,
            domainEventPublisher = domainEventPublisher,
        )
    }

    @Bean
    fun jwtAuthenticationFilter(
        tokenProvider: TokenProvider,
        tokenStore: TokenStore,
    ): FilterRegistrationBean<JwtAuthenticationFilter> {
        val filter = JwtAuthenticationFilter(tokenProvider, tokenStore)
        val registration = FilterRegistrationBean(filter)
        registration.addUrlPatterns("/api/*")
        registration.order = 1
        return registration
    }
}
