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
package com.jstore.authentication.spring

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.config.AuthenticationConfigurer
import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.user.domain.useraccount.TokenStore
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(AccessTokenVerifier::class)
class AuthenticationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun authenticationInterceptor(
        accessTokenVerifier: AccessTokenVerifier,
        tokenStore: ObjectProvider<TokenStore>,
        configurers: List<AuthenticationConfigurer>,
        objectMapper: ObjectMapper,
    ): AuthenticationInterceptor {
        return AuthenticationInterceptor(
            accessTokenVerifier,
            tokenStore.ifAvailable,
            configurers,
            objectMapper,
        )
    }

    @Bean
    @ConditionalOnMissingBean
    fun currentPrincipalArgumentResolver(): CurrentPrincipalArgumentResolver {
        return CurrentPrincipalArgumentResolver()
    }

    @Bean
    fun authenticationWebMvcConfigurer(
        interceptor: AuthenticationInterceptor,
        resolver: CurrentPrincipalArgumentResolver,
    ): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(interceptor).addPathPatterns("/**")
            }

            override fun addArgumentResolvers(
                resolvers: MutableList<HandlerMethodArgumentResolver>
            ) {
                resolvers.add(resolver)
            }
        }
    }
}
