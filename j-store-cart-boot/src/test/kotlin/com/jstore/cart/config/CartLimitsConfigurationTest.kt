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
package com.jstore.cart.config

import com.alibaba.nacos.api.config.ConfigService
import com.alibaba.nacos.api.config.listener.Listener
import com.jstore.cart.domain.CartLimits
import com.jstore.cart.service.CartLimitsProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class CartLimitsConfigurationTest {
    private val runner =
        ApplicationContextRunner().withUserConfiguration(CartLimitsConfiguration::class.java)

    @Test
    fun `missing properties use starter defaults without connecting to Nacos`() {
        runner.run { context ->
            assertThat(context).hasNotFailed().doesNotHaveBean(ConfigService::class.java)
            assertThat(context.getBean(CartLimitsProvider::class.java).current())
                .isEqualTo(CartLimits(1, 999, 100))
        }
    }

    @Test
    fun `partial local override retains unspecified defaults`() {
        runner.withPropertyValues("jstore.cart.limits.max-quantity=1500").run { context ->
            assertThat(context.getBean(CartLimitsProvider::class.java).current())
                .isEqualTo(CartLimits(1, 1500, 100))
        }
    }

    @Test
    fun `invalid local configuration fails startup`() {
        for (value in
            listOf("min-quantity=0", "max-quantity=0", "max-lines=0", "max-quantity=oops")) {
            runner.withPropertyValues("jstore.cart.limits.$value").run { context ->
                assertThat(context).hasFailed()
            }
        }
    }

    @Test
    fun `Nacos refresh validates whole snapshots and preserves last valid configuration`() {
        val client = mock<ConfigService>()
        val listener = argumentCaptor<Listener>()
        whenever(
                client.getConfigAndSignListener(
                    eq("j-store-cart.properties"),
                    eq("DEFAULT_GROUP"),
                    eq(3000L),
                    listener.capture(),
                )
            )
            .thenReturn("jstore.cart.limits.max-quantity=1500")
        runner
            .withBean(ConfigService::class.java, { client })
            .withPropertyValues("jstore.cart.nacos.enabled=true", "jstore.cart.limits.max-lines=8")
            .run { context ->
                assertThat(context).hasNotFailed()
                val provider = context.getBean(CartLimitsProvider::class.java)
                assertThat(provider.current()).isEqualTo(CartLimits(1, 1500, 8))
                val beforeRefresh = provider.current()
                listener.firstValue.receiveConfigInfo(
                    "jstore.cart.limits.min-quantity=2\njstore.cart.limits.max-quantity=5\njstore.cart.limits.max-lines=3"
                )
                assertThat(provider.current()).isEqualTo(CartLimits(2, 5, 3))
                assertThat(beforeRefresh).isEqualTo(CartLimits(1, 1500, 8))
                for (invalid in
                    listOf(
                        null,
                        "",
                        "jstore.cart.limits.max-quantity=oops",
                        "jstore.cart.limits.min-quantity=1000",
                        "jstore.cart.limits.max-lines=0",
                        "jstore.cart.limits.max-line=3",
                    )) {
                    listener.firstValue.receiveConfigInfo(invalid)
                    assertThat(provider.current()).isEqualTo(CartLimits(2, 5, 3))
                }
                listener.firstValue.receiveConfigInfo("jstore.cart.limits.max-quantity=2000")
                assertThat(provider.current()).isEqualTo(CartLimits(1, 2000, 8))
            }
        verify(client)
            .removeListener("j-store-cart.properties", "DEFAULT_GROUP", listener.firstValue)
    }

    @Test
    fun `missing Nacos document fails startup`() {
        val client = mock<ConfigService>()
        runner
            .withBean(ConfigService::class.java, { client })
            .withPropertyValues("jstore.cart.nacos.enabled=true")
            .run { context -> assertThat(context).hasFailed() }
        verify(client).removeListener(eq("j-store-cart.properties"), eq("DEFAULT_GROUP"), any())
    }
}
