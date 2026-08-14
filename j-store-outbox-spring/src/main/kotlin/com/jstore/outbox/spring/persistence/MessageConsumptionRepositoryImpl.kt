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

import com.jstore.messaging.BuiltInMessageConsumerIds
import com.jstore.messaging.MessageConsumptionRepository
import com.jstore.messaging.MessageConsumptionRetentionRepository
import com.jstore.messaging.MessageDeliveryOrder
import com.jstore.messaging.MessageSequenceGapException
import jakarta.persistence.EntityManager
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class MessageConsumptionRepositoryImpl(private val entityManager: EntityManager) :
    MessageConsumptionRepository, MessageConsumptionRetentionRepository {

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

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun tryStartOrdered(
        consumerId: String,
        messageId: String,
        messageName: String,
        messageVersion: Int,
        deliveryOrder: MessageDeliveryOrder,
    ): Boolean {
        val now = Instant.now()
        entityManager
            .createNativeQuery(
                """
                INSERT INTO message_stream_consumption
                    (consumer_id, transport_id, ordering_key, last_sequence_no, updated_at)
                VALUES (:consumerId, :transportId, :orderingKey, :initialSequenceNo, :now)
                ON CONFLICT (consumer_id, transport_id, ordering_key) DO NOTHING
                """
                    .trimIndent()
            )
            .setParameter("consumerId", consumerId)
            .setParameter("transportId", deliveryOrder.transportId)
            .setParameter("orderingKey", deliveryOrder.orderingKey)
            .setParameter("initialSequenceNo", initialSequenceNo(consumerId, deliveryOrder))
            .setParameter("now", now)
            .executeUpdate()
        val lastSequence =
            (entityManager
                    .createNativeQuery(
                        """
                        SELECT last_sequence_no
                        FROM message_stream_consumption
                        WHERE consumer_id = :consumerId
                          AND transport_id = :transportId
                          AND ordering_key = :orderingKey
                        FOR UPDATE
                        """
                            .trimIndent()
                    )
                    .setParameter("consumerId", consumerId)
                    .setParameter("transportId", deliveryOrder.transportId)
                    .setParameter("orderingKey", deliveryOrder.orderingKey)
                    .singleResult as Number)
                .toLong()
        val expected = lastSequence + 1
        if (deliveryOrder.sequenceNo <= lastSequence) {
            return false
        }
        if (deliveryOrder.sequenceNo != expected) {
            throw MessageSequenceGapException(
                consumerId,
                deliveryOrder.transportId,
                deliveryOrder.orderingKey,
                expected,
                deliveryOrder.sequenceNo,
            )
        }
        val accepted = tryStart(consumerId, messageId, messageName, messageVersion)
        val lastAcceptedSequence =
            if (consumerId == BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS) {
                localOutboxPublishedPrefixEnd(deliveryOrder)
            } else {
                deliveryOrder.sequenceNo
            }
        entityManager
            .createNativeQuery(
                """
                UPDATE message_stream_consumption
                SET last_sequence_no = :sequenceNo, updated_at = :now
                WHERE consumer_id = :consumerId
                  AND transport_id = :transportId
                  AND ordering_key = :orderingKey
                """
                    .trimIndent()
            )
            .setParameter("sequenceNo", lastAcceptedSequence)
            .setParameter("now", now)
            .setParameter("consumerId", consumerId)
            .setParameter("transportId", deliveryOrder.transportId)
            .setParameter("orderingKey", deliveryOrder.orderingKey)
            .executeUpdate()
        return accepted
    }

    @Transactional
    open override fun deleteConsumptionsBefore(before: Instant, batchSize: Int): Int {
        require(batchSize > 0) { "batchSize must be positive" }
        return entityManager
            .createNativeQuery(
                """
                DELETE FROM domain_event_consumption
                WHERE ctid IN (
                    SELECT ctid
                    FROM domain_event_consumption
                    WHERE consumed_at < :before
                    ORDER BY consumed_at
                    LIMIT :batchSize
                    FOR UPDATE SKIP LOCKED
                )
                """
                    .trimIndent()
            )
            .setParameter("before", before)
            .setParameter("batchSize", batchSize)
            .executeUpdate()
    }

    @Transactional
    open override fun deleteInactiveStreamPositionsBefore(
        before: Instant,
        batchSize: Int,
    ): Int {
        require(batchSize > 0) { "batchSize must be positive" }
        return entityManager
            .createNativeQuery(
                """
                DELETE FROM message_stream_consumption consumption
                WHERE (consumer_id, transport_id, ordering_key) IN (
                    SELECT candidate.consumer_id,
                           candidate.transport_id,
                           candidate.ordering_key
                    FROM message_stream_consumption candidate
                    WHERE candidate.updated_at < :before
                      AND candidate.consumer_id = :localConsumerId
                      AND NOT EXISTS (
                          SELECT 1
                          FROM outbox_entry entry
                          WHERE entry.transport_id = candidate.transport_id
                            AND entry.ordering_key = candidate.ordering_key
                            AND entry.status <> 'PUBLISHED'
                      )
                    ORDER BY candidate.updated_at
                    LIMIT :batchSize
                    FOR UPDATE OF candidate SKIP LOCKED
                )
                """
                    .trimIndent()
            )
            .setParameter("before", before)
            .setParameter("batchSize", batchSize)
            .setParameter("localConsumerId", BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS)
            .executeUpdate()
    }

    private fun initialSequenceNo(
        consumerId: String,
        deliveryOrder: MessageDeliveryOrder,
    ): Long {
        if (consumerId != BuiltInMessageConsumerIds.LOCAL_INTEGRATION_BUS) return 0
        val hasUnfinishedPredecessor =
            (entityManager
                .createNativeQuery(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM outbox_entry entry
                        WHERE entry.transport_id = :transportId
                          AND entry.ordering_key = :orderingKey
                          AND entry.sequence_no < :sequenceNo
                          AND entry.status <> 'PUBLISHED'
                    )
                    """
                        .trimIndent()
                )
                .setParameter("transportId", deliveryOrder.transportId)
                .setParameter("orderingKey", deliveryOrder.orderingKey)
                .setParameter("sequenceNo", deliveryOrder.sequenceNo)
                .singleResult as Boolean)
        return if (hasUnfinishedPredecessor) 0 else deliveryOrder.sequenceNo - 1
    }

    private fun localOutboxPublishedPrefixEnd(deliveryOrder: MessageDeliveryOrder): Long =
        (entityManager
                .createNativeQuery(
                    """
                    SELECT COALESCE(
                        (
                            SELECT COALESCE(
                                MIN(entry.sequence_no) FILTER (
                                    WHERE entry.sequence_no > :sequenceNo
                                      AND entry.status <> 'PUBLISHED'
                                ) - 1,
                                position.last_sequence_no
                            )
                            FROM outbox_stream_position position
                            LEFT JOIN outbox_entry entry
                              ON entry.transport_id = position.transport_id
                             AND entry.ordering_key = position.ordering_key
                            WHERE position.transport_id = :transportId
                              AND position.ordering_key = :orderingKey
                            GROUP BY position.last_sequence_no
                        ),
                        :sequenceNo
                    )
                    """
                        .trimIndent()
                )
                .setParameter("transportId", deliveryOrder.transportId)
                .setParameter("orderingKey", deliveryOrder.orderingKey)
                .setParameter("sequenceNo", deliveryOrder.sequenceNo)
                .singleResult as Number)
            .toLong()
}
