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

import com.jstore.outbox.OutboxStreamKey
import com.jstore.outbox.OutboxStreamSequenceAllocator
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class PostgresOutboxStreamSequenceAllocator(private val entityManager: EntityManager) :
    OutboxStreamSequenceAllocator {

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun nextSequence(transportId: String, orderingKey: String): Long {
        return allocateSequenceRange(OutboxStreamKey(transportId, orderingKey), 1).last
    }

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun nextSequences(streams: List<OutboxStreamKey>): List<Long> {
        if (streams.isEmpty()) return emptyList()

        val counts = linkedMapOf<OutboxStreamKey, Int>()
        streams.forEach { stream -> counts[stream] = counts.getOrDefault(stream, 0) + 1 }
        val nextByStream =
            counts.mapValuesTo(linkedMapOf()) { (stream, count) ->
                allocateSequenceRange(stream, count).first
            }

        return streams.map { stream ->
            val next = checkNotNull(nextByStream[stream])
            nextByStream[stream] = next + 1
            next
        }
    }

    private fun allocateSequenceRange(stream: OutboxStreamKey, count: Int): LongRange {
        require(count > 0) { "count must be greater than zero" }
        return (entityManager
                .createNativeQuery(
                    """
                    INSERT INTO outbox_stream_position
                        (transport_id, ordering_key, last_sequence_no)
                    VALUES (:transportId, :orderingKey, :count)
                    ON CONFLICT (transport_id, ordering_key)
                    DO UPDATE SET last_sequence_no =
                        outbox_stream_position.last_sequence_no + :count
                    RETURNING last_sequence_no
                    """
                        .trimIndent()
                )
                .setParameter("transportId", stream.transportId)
                .setParameter("orderingKey", stream.orderingKey)
                .setParameter("count", count)
                .singleResult as Number)
            .toLong()
            .let { end -> (end - count + 1)..end }
    }
}
