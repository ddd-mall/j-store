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

import com.jstore.cart.domain.CartRefreshRequestedEvent
import com.jstore.cart.service.CartRefreshRequestedHandler
import com.jstore.common.framework.event.PreparingDomainEventListener
import java.util.Optional
import org.springframework.transaction.support.TransactionSynchronizationManager

/** The Outbox delivery transaction owns completion and consumption acknowledgement together. */
class TransactionalCartRefreshRequestedHandler(
    private val delegate: CartRefreshRequestedHandler,
    private val transactions: CartTransactionOperations,
) : PreparingDomainEventListener<CartRefreshRequestedEvent> {
    override fun listenerId() = delegate.listenerId()

    override fun prepare(event: CartRefreshRequestedEvent): () -> Unit {
        check(!TransactionSynchronizationManager.isActualTransactionActive()) {
            "Cart refresh preparation must run before the delivery transaction"
        }
        val start = transactions.read { Optional.ofNullable(delegate.start(event)) }
        if (start.isEmpty) return {}
        val cart = start.get()
        val facts = transactions.withoutTransaction { delegate.collect(cart) }
        return { delegate.complete(cart, facts) }
    }

    override fun onDomainEvent(event: CartRefreshRequestedEvent) {
        error("Cart refresh requires prepared Outbox delivery")
    }
}
