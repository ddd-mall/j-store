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
package com.jstore.outbox.spring.polling

import com.jstore.outbox.OutboxMessage
import com.jstore.outbox.OutboxWriter
import com.jstore.outbox.spring.OutboxRelaySignal
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** Polling-specific task initialization and post-commit wake-up. */
open class PollingOutboxWriter(
    private val repository: OutboxEntryRepository,
    private val signal: OutboxRelaySignal,
) : OutboxWriter {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun append(messages: List<OutboxMessage>) {
        if (messages.isEmpty()) return
        repository.saveAll(messages.map(OutboxEntry::pending))
        signal.signalAfterCommit()
    }
}
