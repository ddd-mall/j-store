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

import com.alibaba.nacos.api.NacosFactory
import com.alibaba.nacos.api.config.ConfigService
import com.jstore.cart.service.CartLimitsProvider
import java.util.Properties
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CartLimitsProperties::class, CartNacosProperties::class)
class CartLimitsConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "jstore.cart.nacos",
        name = ["enabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    @ConditionalOnMissingBean(CartLimitsProvider::class)
    fun localCartLimitsProvider(properties: CartLimitsProperties): CartLimitsProvider {
        val limits = properties.toLimits()
        return CartLimitsProvider { limits }
    }

    @Bean(destroyMethod = "shutDown")
    @ConditionalOnProperty(prefix = "jstore.cart.nacos", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(ConfigService::class)
    fun cartNacosConfigService(properties: CartNacosProperties): ConfigService {
        require(properties.serverAddr.isNotBlank()) { "jstore.cart.nacos.server-addr is required" }
        return NacosFactory.createConfigService(
            Properties().apply {
                setProperty("serverAddr", properties.serverAddr)
                setProperty("namespace", properties.namespace)
                setProperty("username", properties.username)
                setProperty("password", properties.password)
            }
        )
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "jstore.cart.nacos", name = ["enabled"], havingValue = "true")
    @ConditionalOnMissingBean(CartLimitsProvider::class)
    fun nacosCartLimitsProvider(
        properties: CartLimitsProperties,
        nacos: CartNacosProperties,
        configService: ConfigService,
    ): NacosCartLimitsProvider =
        NacosCartLimitsProvider(properties.toLimits(), nacos, configService)
}
