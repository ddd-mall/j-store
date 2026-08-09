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
package com.jstore.fulfillment.config

import com.jstore.fulfillment.service.FulfillmentRequest
import com.jstore.fulfillment.service.FulfillmentUseCase
import java.time.Instant
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalFulfillmentUseCase(
    private val delegate: FulfillmentUseCase,
    transactionManager: PlatformTransactionManager,
) : FulfillmentUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun createForOrder(request: FulfillmentRequest) = tx {
        delegate.createForOrder(request)
    }

    override fun getByOrderId(orderId: Long) = query { delegate.getByOrderId(orderId) }

    override fun prepare(orderId: Long, occurredAt: Instant) = tx {
        delegate.prepare(orderId, occurredAt)
    }

    override fun dispatch(
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ) = tx { delegate.dispatch(orderId, carrierCode, trackingNumber, occurredAt) }

    override fun deliver(orderId: Long, occurredAt: Instant) = tx {
        delegate.deliver(orderId, occurredAt)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
