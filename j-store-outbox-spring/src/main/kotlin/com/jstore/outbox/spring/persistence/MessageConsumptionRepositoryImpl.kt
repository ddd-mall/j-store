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
package com.jstore.outbox.spring.persistence

import com.jstore.messaging.MessageConsumptionRepository
import jakarta.persistence.EntityManager
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class MessageConsumptionRepositoryImpl(private val entityManager: EntityManager) :
    MessageConsumptionRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun tryStart(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
    ): Boolean {
        val inserted =
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO domain_event_consumption (listener_id, event_id, event_name, event_version, consumed_at)
                    VALUES (:listenerId, :eventId, :eventName, :eventVersion, :consumedAt)
                    ON CONFLICT (listener_id, event_id) DO NOTHING
                    """
                        .trimIndent()
                )
                .setParameter("listenerId", consumerId)
                .setParameter("eventId", messageId)
                .setParameter("eventName", messageName)
                .setParameter("eventVersion", messageVersion)
                .setParameter("consumedAt", Instant.now())
                .executeUpdate()
        return inserted == 1
    }
}
