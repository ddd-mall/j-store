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
package com.jstore.common.framework.event.outbox

import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

interface OutboxRelayTransactionOperations {
    fun <T> executeDelivery(action: () -> T): T

    fun <T> executeFailure(action: () -> T): T
}

object ImmediateOutboxRelayTransactionOperations : OutboxRelayTransactionOperations {
    override fun <T> executeDelivery(action: () -> T): T = action()

    override fun <T> executeFailure(action: () -> T): T = action()
}

class SpringOutboxRelayTransactionOperations(transactionManager: PlatformTransactionManager) :
    OutboxRelayTransactionOperations {
    private val deliveryTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRED
        }
    private val failureTransaction =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }

    override fun <T> executeDelivery(action: () -> T): T {
        return deliveryTransaction.execute { action() }
            ?: throw IllegalStateException("Outbox delivery transaction returned null")
    }

    override fun <T> executeFailure(action: () -> T): T {
        return failureTransaction.execute { action() }
            ?: throw IllegalStateException("Outbox failure transaction returned null")
    }
}
