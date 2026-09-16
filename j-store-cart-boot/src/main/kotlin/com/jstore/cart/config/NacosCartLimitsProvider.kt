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
import java.io.StringReader
import java.util.Properties
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource

class NacosCartLimitsProvider(
    private val local: CartLimits,
    private val properties: CartNacosProperties,
    private val client: ConfigService,
) : CartLimitsProvider, AutoCloseable {
    private val current = AtomicReference(local)
    private val refreshExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "cart-limits-refresh").apply { isDaemon = true }
    }
    private val logger = LoggerFactory.getLogger(javaClass)
    private val listener =
        object : Listener {
            override fun getExecutor() = refreshExecutor

            override fun receiveConfigInfo(configInfo: String?) {
                synchronized(this@NacosCartLimitsProvider) {
                    try {
                        current.set(parse(configInfo))
                        logger.info("Cart limits configuration refreshed")
                    } catch (_: RuntimeException) {
                        logger.warn(
                            "Rejected invalid Cart limits configuration; retaining last valid snapshot"
                        )
                    }
                }
            }
        }

    init {
        try {
            require(properties.timeoutMs > 0) { "Nacos timeout must be positive" }
            require(properties.dataId.isNotBlank() && properties.group.isNotBlank()) {
                "Nacos data-id and group must not be blank"
            }
            synchronized(this) {
                current.set(
                    parse(
                        client.getConfigAndSignListener(
                            properties.dataId,
                            properties.group,
                            properties.timeoutMs,
                            listener,
                        )
                    )
                )
            }
        } catch (failure: Exception) {
            close()
            throw failure
        }
    }

    override fun current(): CartLimits = current.get()

    override fun close() {
        try {
            client.removeListener(properties.dataId, properties.group, listener)
        } finally {
            refreshExecutor.shutdownNow()
        }
    }

    private fun parse(content: String?): CartLimits {
        require(!content.isNullOrBlank()) {
            "Nacos Cart limits configuration must exist and not be empty"
        }
        val remote = Properties().apply { load(StringReader(content)) }
        val keys =
            setOf(
                "jstore.cart.limits.min-quantity",
                "jstore.cart.limits.max-quantity",
                "jstore.cart.limits.max-lines",
            )
        require(remote.isNotEmpty() && remote.stringPropertyNames().all { it in keys }) {
            "Nacos Cart limits configuration contains no supported settings or unknown keys"
        }
        val candidate =
            CartLimitsProperties().apply {
                minQuantity = local.minQuantity
                maxQuantity = local.maxQuantity
                maxLines = local.maxLines
            }
        Binder(
                MapConfigurationPropertySource(
                    remote.entries.associate { it.key.toString() to it.value }
                )
            )
            .bind("jstore.cart.limits", Bindable.ofInstance(candidate))
        return candidate.toLimits()
    }
}
