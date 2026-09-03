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
import com.jstore.authentication.principal.AccessTokenVerifier
import com.jstore.user.domain.useraccount.TokenStore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.WebApplicationContextRunner

/** 集成测试：验证 AuthenticationAutoConfiguration 的条件激活行为。 _需求: 7.1, 7.3, 7.4_ */
class AuthenticationAutoConfigurationTest {

    private val contextRunner =
        WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AuthenticationAutoConfiguration::class.java))

    @Test
    fun `auto-configuration activates when an AccessTokenVerifier is present`() {
        contextRunner
            .withBean(AccessTokenVerifier::class.java, { mock() })
            .withBean(TokenStore::class.java, { mock() })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertThat(context).hasSingleBean(AuthenticationInterceptor::class.java)
                assertThat(context).hasSingleBean(CurrentPrincipalArgumentResolver::class.java)
            }
    }

    @Test
    fun `auto-configuration does not activate when AccessTokenVerifier is missing`() {
        contextRunner.withBean(TokenStore::class.java, { mock() }).run { context ->
            assertThat(context).doesNotHaveBean(AuthenticationInterceptor::class.java)
            assertThat(context).doesNotHaveBean(CurrentPrincipalArgumentResolver::class.java)
        }
    }

    @Test
    fun `auto-configuration supports stateless token verifier without TokenStore`() {
        contextRunner
            .withBean(AccessTokenVerifier::class.java, { mock() })
            .withBean(ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertThat(context).hasSingleBean(AuthenticationInterceptor::class.java)
                assertThat(context).hasSingleBean(CurrentPrincipalArgumentResolver::class.java)
            }
    }

    @Test
    fun `auto-configuration does not activate when both beans are missing`() {
        contextRunner.run { context ->
            assertThat(context).doesNotHaveBean(AuthenticationInterceptor::class.java)
            assertThat(context).doesNotHaveBean(CurrentPrincipalArgumentResolver::class.java)
        }
    }
}
