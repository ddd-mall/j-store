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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

class TransactionAwareOutboxRelaySignalTest :
    FunSpec({
        afterTest {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization()
            }
            TransactionSynchronizationManager.clear()
        }

        test("requests relay only after transaction commit") {
            val trigger = mock<OutboxRelayTrigger>()
            val signal = TransactionAwareOutboxRelaySignal(trigger)
            TransactionSynchronizationManager.initSynchronization()

            signal.signalAfterCommit()

            verify(trigger, never()).requestDrain()
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
            verify(trigger).requestDrain()
        }

        test("rollback does not request relay") {
            val trigger = mock<OutboxRelayTrigger>()
            val signal = TransactionAwareOutboxRelaySignal(trigger)
            TransactionSynchronizationManager.initSynchronization()

            signal.signalAfterCommit()

            TransactionSynchronizationManager.getSynchronizations().forEach {
                it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            }
            verify(trigger, never()).requestDrain()
        }

        test("multiple publications in one transaction register one wake-up") {
            val trigger = mock<OutboxRelayTrigger>()
            val signal = TransactionAwareOutboxRelaySignal(trigger)
            TransactionSynchronizationManager.initSynchronization()

            repeat(20) { signal.signalAfterCommit() }
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }

            verify(trigger).requestDrain()
        }

        test("relay wake-up failure does not escape the after-commit callback") {
            val signal = TransactionAwareOutboxRelaySignal {
                throw IllegalStateException("executor unavailable")
            }
            TransactionSynchronizationManager.initSynchronization()

            signal.signalAfterCommit()

            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCommit() }
        }

        test("requires transaction synchronization") {
            val signal = TransactionAwareOutboxRelaySignal(mock())

            shouldThrow<IllegalStateException> { signal.signalAfterCommit() }
        }
    })
