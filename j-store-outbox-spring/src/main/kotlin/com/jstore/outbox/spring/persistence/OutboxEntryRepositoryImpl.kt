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

import com.jstore.outbox.OutboxEntry
import com.jstore.outbox.OutboxEntryRepository
import com.jstore.outbox.OutboxEntryStatus
import com.jstore.outbox.spring.*
import jakarta.persistence.EntityManager
import java.time.Instant
import org.springframework.transaction.annotation.Transactional

open class OutboxEntryRepositoryImpl(
    private val jpaRepository: OutboxEntryPOJpaRepository,
    private val entityManager: EntityManager,
) : OutboxEntryRepository, OutboxDeadLetterOperationsRepository {

    override fun save(entry: OutboxEntry): OutboxEntry {
        val po = Converter.toPO(entry)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun saveAll(entries: List<OutboxEntry>): List<OutboxEntry> =
        jpaRepository.saveAll(entries.map(Converter::toPO)).map(Converter::toDomain)

    @Transactional
    open override fun claimPendingAndRetryable(
        maxRetryCount: Int,
        batchSize: Int,
        lockedBy: String,
        lockedUntil: Instant,
    ): List<OutboxEntry> {
        val now = Instant.now()
        val claimed =
            entityManager
                .createNativeQuery(
                    """
                    WITH expired_exhausted AS (
                        UPDATE outbox_entry
                        SET status = 'DEAD_LETTER',
                            updated_at = :now,
                            locked_by = NULL,
                            locked_at = NULL,
                            locked_until = NULL,
                            last_error = 'Outbox relay lock expired after max retry count'
                        WHERE status = 'IN_PROGRESS'
                          AND locked_until < :now
                          AND retry_count >= :maxRetryCount
                    ),
                    candidates AS (
                        SELECT e.id
                        FROM outbox_entry e
                        WHERE (
                               e.status = 'PENDING'
                            OR (e.status = 'FAILED' AND e.retry_count < :maxRetryCount AND e.next_attempt_at <= :now)
                            OR (e.status = 'IN_PROGRESS' AND e.retry_count < :maxRetryCount AND e.locked_until < :now)
                        )
                          AND NOT EXISTS (
                            SELECT 1
                            FROM outbox_entry predecessor
                            WHERE predecessor.transport_id = e.transport_id
                              AND predecessor.ordering_key = e.ordering_key
                              AND predecessor.status <> 'PUBLISHED'
                              AND predecessor.sequence_no < e.sequence_no
                          )
                        ORDER BY e.created_at ASC, e.id ASC
                        FOR UPDATE SKIP LOCKED
                        LIMIT :batchSize
                    )
                    UPDATE outbox_entry e
                    SET status = 'IN_PROGRESS',
                        retry_count = e.retry_count + 1,
                        locked_by = :lockedBy,
                        locked_at = :now,
                        locked_until = :lockedUntil,
                        lock_token = e.lock_token + 1,
                        updated_at = :now
                    FROM candidates
                    WHERE e.id = candidates.id
                    RETURNING e.*
                    """
                        .trimIndent(),
                    OutboxEntryPO::class.java,
                )
                .setParameter("maxRetryCount", maxRetryCount)
                .setParameter("batchSize", batchSize)
                .setParameter("lockedBy", lockedBy)
                .setParameter("lockedUntil", lockedUntil)
                .setParameter("now", now)
                .resultList

        @Suppress("UNCHECKED_CAST")
        val result = (claimed as List<OutboxEntryPO>).map(Converter::toDomain)
        entityManager.clear()
        return result
    }

    @Transactional
    open override fun renewLease(
        id: String,
        lockedBy: String,
        lockToken: Long,
        lockedUntil: Instant,
    ): Boolean {
        val updated =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE outbox_entry
                    SET locked_until = :lockedUntil,
                        updated_at = :now
                    WHERE id = :id
                      AND status = 'IN_PROGRESS'
                      AND locked_by = :lockedBy
                      AND lock_token = :lockToken
                      AND locked_until >= :now
                    """
                        .trimIndent()
                )
                .setParameter("id", id)
                .setParameter("lockedBy", lockedBy)
                .setParameter("lockToken", lockToken)
                .setParameter("lockedUntil", lockedUntil)
                .setParameter("now", Instant.now())
                .executeUpdate() == 1
        entityManager.clear()
        return updated
    }

    @Transactional
    open override fun markPublished(entry: OutboxEntry, lockedBy: String): Boolean {
        val updated =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE outbox_entry
                    SET status = 'PUBLISHED',
                        updated_at = :updatedAt,
                        locked_by = NULL,
                        locked_at = NULL,
                        locked_until = NULL,
                        last_error = NULL
                    WHERE id = :id
                      AND status = 'IN_PROGRESS'
                      AND locked_by = :lockedBy
                      AND lock_token = :lockToken
                      AND locked_until >= :now
                    """
                        .trimIndent()
                )
                .setParameter("id", entry.id)
                .setParameter("lockedBy", lockedBy)
                .setParameter("lockToken", entry.lockToken)
                .setParameter("updatedAt", entry.updatedAt)
                .setParameter("now", Instant.now())
                .executeUpdate() == 1
        entityManager.clear()
        return updated
    }

    @Transactional
    open override fun markFailed(entry: OutboxEntry, lockedBy: String): Boolean {
        val updated =
            entityManager
                .createNativeQuery(
                    """
                    UPDATE outbox_entry
                    SET status = :status,
                        retry_count = :retryCount,
                        next_attempt_at = :nextAttemptAt,
                        updated_at = :updatedAt,
                        locked_by = NULL,
                        locked_at = NULL,
                        locked_until = NULL,
                        last_error = :lastError
                    WHERE id = :id
                      AND status = 'IN_PROGRESS'
                      AND locked_by = :lockedBy
                      AND lock_token = :lockToken
                      AND locked_until >= :now
                    """
                        .trimIndent()
                )
                .setParameter("id", entry.id)
                .setParameter("lockedBy", lockedBy)
                .setParameter("lockToken", entry.lockToken)
                .setParameter("status", entry.status.name)
                .setParameter("retryCount", entry.retryCount)
                .setParameter("nextAttemptAt", entry.nextAttemptAt)
                .setParameter("updatedAt", entry.updatedAt)
                .setParameter("lastError", entry.lastError)
                .setParameter("now", Instant.now())
                .executeUpdate() == 1
        entityManager.clear()
        return updated
    }

    @Transactional
    open override fun deletePublishedBefore(before: Instant, batchSize: Int): Int {
        val deleted =
            entityManager
                .createNativeQuery(
                    """
                    DELETE FROM outbox_entry
                    WHERE id IN (
                        SELECT id
                        FROM outbox_entry
                        WHERE status = 'PUBLISHED' AND created_at < :before
                        ORDER BY created_at ASC
                        LIMIT :batchSize
                    )
                    """
                        .trimIndent()
                )
                .setParameter("before", before)
                .setParameter("batchSize", batchSize)
                .executeUpdate()
        entityManager.clear()
        return deleted
    }

    override fun findDeadLetters(batchSize: Int): List<OutboxEntry> {
        val deadLetters =
            entityManager
                .createNativeQuery(
                    """
                    SELECT *
                    FROM outbox_entry
                    WHERE status = 'DEAD_LETTER'
                    ORDER BY updated_at ASC
                    LIMIT :batchSize
                    """
                        .trimIndent(),
                    OutboxEntryPO::class.java,
                )
                .setParameter("batchSize", batchSize)
                .resultList

        @Suppress("UNCHECKED_CAST")
        return (deadLetters as List<OutboxEntryPO>).map(Converter::toDomain)
    }

    override fun countByStatus(status: OutboxEntryStatus): Long {
        return entityManager
            .createQuery(
                "SELECT COUNT(e) FROM OutboxEntryPO e WHERE e.status = :status",
                java.lang.Long::class.java,
            )
            .setParameter("status", status)
            .singleResult
            .toLong()
    }

    override fun countByStatus(status: OutboxEntryStatus, transportId: String): Long =
        entityManager
            .createQuery(
                "SELECT COUNT(e) FROM OutboxEntryPO e " +
                    "WHERE e.status = :status AND e.transportId = :transportId",
                java.lang.Long::class.java,
            )
            .setParameter("status", status)
            .setParameter("transportId", transportId)
            .singleResult
            .toLong()

    override fun findDeadLetters(page: Int, size: Int): OutboxDeadLetterPage {
        require(page >= 1) { "page must be greater than or equal to 1" }
        require(size > 0) { "size must be greater than 0" }
        val entries =
            entityManager
                .createQuery(
                    "SELECT e FROM OutboxEntryPO e WHERE e.status = :status ORDER BY e.updatedAt ASC, e.id ASC",
                    OutboxEntryPO::class.java,
                )
                .setParameter("status", OutboxEntryStatus.DEAD_LETTER)
                .setFirstResult((page - 1) * size)
                .setMaxResults(size)
                .resultList
                .map { e ->
                    OutboxDeadLetterSummary(
                        e.id,
                        e.eventId,
                        e.eventType,
                        e.aggregateType,
                        e.aggregateId,
                        e.eventVersion,
                        e.occurredAt,
                        e.createdAt,
                        e.updatedAt,
                        e.retryCount,
                        e.lastError,
                        e.transportId,
                        e.orderingKey,
                        e.sequenceNo,
                    )
                }
        return OutboxDeadLetterPage(
            entries,
            page,
            size,
            countByStatus(OutboxEntryStatus.DEAD_LETTER),
        )
    }

    @Transactional
    open override fun requeueDeadLetters(
        ids: Collection<String>,
        operatorId: String,
        reason: String,
        nextAttemptAt: Instant,
    ): DeadLetterRequeueResult {
        val targets = ids.distinct()
        var requeued = 0
        targets.forEach { id ->
            val eventId =
                entityManager
                    .createNativeQuery("SELECT event_id FROM outbox_entry WHERE id = :id")
                    .setParameter("id", id)
                    .resultList
                    .firstOrNull() as String?
            val updated =
                entityManager
                    .createNativeQuery(
                        """
                        UPDATE outbox_entry SET status = 'FAILED', retry_count = 0,
                            next_attempt_at = :nextAttemptAt, locked_by = NULL, locked_at = NULL,
                            locked_until = NULL, last_error = NULL, updated_at = :now
                        WHERE id = :id AND status = 'DEAD_LETTER'
                        """
                            .trimIndent()
                    )
                    .setParameter("id", id)
                    .setParameter("nextAttemptAt", nextAttemptAt)
                    .setParameter("now", Instant.now())
                    .executeUpdate()
            requeued += updated
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO outbox_dead_letter_audit
                        (outbox_entry_id, event_id, operator_id, action, reason, result, created_at)
                    VALUES (:id, :eventId, :operatorId, 'REQUEUE', :reason, :result, :now)
                    """
                        .trimIndent()
                )
                .setParameter("id", id)
                .setParameter("eventId", eventId)
                .setParameter("operatorId", operatorId)
                .setParameter("reason", reason)
                .setParameter(
                    "result",
                    if (updated == 1) OutboxDeadLetterAuditResult.REQUEUED.name
                    else OutboxDeadLetterAuditResult.NOT_REQUEUED.name,
                )
                .setParameter("now", Instant.now())
                .executeUpdate()
        }
        entityManager.clear()
        return DeadLetterRequeueResult(requeued, targets.size - requeued)
    }

    override fun findOldestReadyAt(now: Instant, maxRetryCount: Int): Instant? =
        entityManager
            .createQuery(
                """
                SELECT MIN(e.createdAt) FROM OutboxEntryPO e
                WHERE e.status = :pending
                   OR (e.status = :failed AND e.retryCount < :maxRetryCount AND e.nextAttemptAt <= :now)
                   OR (e.status = :inProgress AND e.retryCount < :maxRetryCount AND e.lockedUntil < :now)
                """
                    .trimIndent(),
                Instant::class.java,
            )
            .setParameter("pending", OutboxEntryStatus.PENDING)
            .setParameter("failed", OutboxEntryStatus.FAILED)
            .setParameter("inProgress", OutboxEntryStatus.IN_PROGRESS)
            .setParameter("maxRetryCount", maxRetryCount)
            .setParameter("now", now)
            .singleResult

    override fun findOldestReadyAt(
        now: Instant,
        maxRetryCount: Int,
        transportId: String,
    ): Instant? =
        entityManager
            .createQuery(
                """
                SELECT MIN(e.createdAt) FROM OutboxEntryPO e
                WHERE e.transportId = :transportId
                  AND (e.status = :pending
                   OR (e.status = :failed AND e.retryCount < :maxRetryCount AND e.nextAttemptAt <= :now)
                   OR (e.status = :inProgress AND e.retryCount < :maxRetryCount AND e.lockedUntil < :now))
                """
                    .trimIndent(),
                Instant::class.java,
            )
            .setParameter("transportId", transportId)
            .setParameter("pending", OutboxEntryStatus.PENDING)
            .setParameter("failed", OutboxEntryStatus.FAILED)
            .setParameter("inProgress", OutboxEntryStatus.IN_PROGRESS)
            .setParameter("maxRetryCount", maxRetryCount)
            .setParameter("now", now)
            .singleResult

    override fun countExpiredLocks(now: Instant): Long =
        entityManager
            .createQuery(
                "SELECT COUNT(e) FROM OutboxEntryPO e WHERE e.status = :status AND e.lockedUntil < :now",
                java.lang.Long::class.java,
            )
            .setParameter("status", OutboxEntryStatus.IN_PROGRESS)
            .setParameter("now", now)
            .singleResult
            .toLong()

    override fun countExpiredLocks(now: Instant, transportId: String): Long =
        entityManager
            .createQuery(
                "SELECT COUNT(e) FROM OutboxEntryPO e " +
                    "WHERE e.status = :status AND e.lockedUntil < :now " +
                    "AND e.transportId = :transportId",
                java.lang.Long::class.java,
            )
            .setParameter("status", OutboxEntryStatus.IN_PROGRESS)
            .setParameter("now", now)
            .setParameter("transportId", transportId)
            .singleResult
            .toLong()

    override fun findTransportIds(): Set<String> =
        entityManager
            .createQuery(
                "SELECT DISTINCT e.transportId FROM OutboxEntryPO e ORDER BY e.transportId",
                String::class.java,
            )
            .resultList
            .toSet()

    private object Converter {
        fun toPO(entry: OutboxEntry) =
            OutboxEntryPO(
                id = entry.id,
                eventId = entry.eventId,
                eventType = entry.eventType,
                eventClassName = entry.eventClassName,
                eventVersion = entry.eventVersion,
                payload = entry.payload,
                aggregateType = entry.aggregateType,
                aggregateId = entry.aggregateId,
                occurredAt = entry.occurredAt,
                status = entry.status,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt,
                retryCount = entry.retryCount,
                nextAttemptAt = entry.nextAttemptAt,
                lockedBy = entry.lockedBy,
                lockedAt = entry.lockedAt,
                lockedUntil = entry.lockedUntil,
                lockToken = entry.lockToken,
                lastError = entry.lastError,
                messageKind = entry.messageKind,
                deliveryTarget = entry.deliveryTarget,
                transportId = entry.transportId,
                destination = entry.destination,
                partitionKey = entry.partitionKey,
                correlationId = entry.correlationId,
                causationId = entry.causationId,
                tenantId = entry.tenantId,
                orderingKey = entry.orderingKey,
                sequenceNo = entry.sequenceNo,
            )

        fun toDomain(po: OutboxEntryPO) =
            OutboxEntry(
                id = po.id,
                eventId = po.eventId.ifBlank { po.id },
                eventType = po.eventType,
                eventClassName = po.eventClassName.ifBlank { po.eventType },
                eventVersion = po.eventVersion,
                payload = po.payload,
                aggregateType = po.aggregateType,
                aggregateId = po.aggregateId,
                occurredAt = po.occurredAt,
                status = po.status,
                createdAt = po.createdAt,
                updatedAt = po.updatedAt,
                retryCount = po.retryCount,
                nextAttemptAt = po.nextAttemptAt,
                lockedBy = po.lockedBy,
                lockedAt = po.lockedAt,
                lockedUntil = po.lockedUntil,
                lockToken = po.lockToken,
                lastError = po.lastError,
                messageKind = po.messageKind,
                deliveryTarget = po.deliveryTarget,
                transportId = po.transportId,
                destination = po.destination.ifBlank { po.eventType },
                partitionKey = po.partitionKey.ifBlank { po.aggregateId },
                correlationId = po.correlationId.ifBlank { po.eventId.ifBlank { po.id } },
                causationId = po.causationId,
                tenantId = po.tenantId,
                orderingKey = po.orderingKey,
                sequenceNo = po.sequenceNo,
            )
    }
}
