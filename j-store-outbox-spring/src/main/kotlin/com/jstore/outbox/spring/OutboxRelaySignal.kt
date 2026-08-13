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
package com.jstore.outbox.spring

import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/** Registers a non-durable relay hint for publication after the current transaction commits. */
fun interface OutboxRelaySignal {
    fun signalAfterCommit()
}

object NoopOutboxRelaySignal : OutboxRelaySignal {
    override fun signalAfterCommit() = Unit
}

class TransactionAwareOutboxRelaySignal(private val trigger: OutboxRelayTrigger) :
    OutboxRelaySignal {
    private val synchronizationResourceKey = Any()

    override fun signalAfterCommit() {
        check(TransactionSynchronizationManager.isSynchronizationActive()) {
            "Outbox relay signal requires active transaction synchronization"
        }
        if (TransactionSynchronizationManager.hasResource(synchronizationResourceKey)) return
        TransactionSynchronizationManager.bindResource(synchronizationResourceKey, true)
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    trigger.requestDrain()
                }

                override fun afterCompletion(status: Int) {
                    TransactionSynchronizationManager.unbindResourceIfPossible(
                        synchronizationResourceKey
                    )
                }
            }
        )
    }
}
