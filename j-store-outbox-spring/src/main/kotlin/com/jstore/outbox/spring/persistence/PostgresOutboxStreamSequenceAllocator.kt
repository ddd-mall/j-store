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

import com.jstore.outbox.OutboxStreamSequenceAllocator
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class PostgresOutboxStreamSequenceAllocator(private val entityManager: EntityManager) :
    OutboxStreamSequenceAllocator {

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun nextSequence(transportId: String, orderingKey: String): Long {
        require(transportId.isNotBlank()) { "transportId must not be blank" }
        require(orderingKey.isNotBlank()) { "orderingKey must not be blank" }
        return (entityManager
                .createNativeQuery(
                    """
                    INSERT INTO outbox_stream_position
                        (transport_id, ordering_key, last_sequence_no)
                    VALUES (:transportId, :orderingKey, 1)
                    ON CONFLICT (transport_id, ordering_key)
                    DO UPDATE SET last_sequence_no =
                        outbox_stream_position.last_sequence_no + 1
                    RETURNING last_sequence_no
                    """
                        .trimIndent()
                )
                .setParameter("transportId", transportId)
                .setParameter("orderingKey", orderingKey)
                .singleResult as Number)
            .toLong()
    }
}
