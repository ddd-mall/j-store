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
package com.jstore.user.controller

import com.jstore.user.service.UserProfileReader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

class InternalUserProfileEndpointConfigurationTest {
    private val runner =
        WebApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(PropertyPlaceholderAutoConfiguration::class.java)
            )
            .withBean(UserProfileReader::class.java, { mock() })
            .withUserConfiguration(TestConfiguration::class.java)

    @Test
    fun `internal endpoint is disabled by default`() {
        runner.run { context ->
            assertThat(context).doesNotHaveBean(InternalUserProfileController::class.java)
        }
    }

    @Test
    fun `enabled endpoint fails fast without its service credential`() {
        runner.withPropertyValues("jstore.user-query.server.enabled=true").run { context ->
            assertThat(context).hasFailed()
        }
    }

    @Test
    fun `enabled endpoint is created with a valid service credential`() {
        runner
            .withPropertyValues(
                "jstore.user-query.server.enabled=true",
                "jstore.user-query.server.token=${"a".repeat(32)}",
            )
            .run { context ->
                assertThat(context).hasSingleBean(InternalUserProfileController::class.java)
            }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(InternalUserProfileController::class)
    private class TestConfiguration
}
