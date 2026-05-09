package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import com.jstore.common.framework.event.outbox.OutboxEntryStatus
import jakarta.persistence.EntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

open class OutboxEntryRepositoryImpl(
    private val jpaRepository: OutboxEntryPOJpaRepository,
    private val entityManager: EntityManager,
) : OutboxEntryRepository {

    override fun save(entry: OutboxEntry): OutboxEntry {
        val po = Converter.toPO(entry)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findPendingAndRetryable(maxRetryCount: Int, batchSize: Int): List<OutboxEntry> {
        return jpaRepository.findPendingAndRetryable(
            maxRetryCount, Instant.now(), PageRequest.of(0, batchSize)
        ).map(Converter::toDomain)
    }

    @Transactional
    open override fun claimPendingAndRetryable(
        maxRetryCount: Int,
        batchSize: Int,
        lockedBy: String,
        lockedUntil: Instant
    ): List<OutboxEntry> {
        val now = Instant.now()
        val claimed = entityManager.createNativeQuery(
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
                SELECT id
                FROM outbox_entry
                WHERE status = 'PENDING'
                   OR (status = 'FAILED' AND retry_count < :maxRetryCount AND next_attempt_at <= :now)
                   OR (status = 'IN_PROGRESS' AND retry_count < :maxRetryCount AND locked_until < :now)
                ORDER BY created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE outbox_entry e
            SET status = 'IN_PROGRESS',
                retry_count = e.retry_count + 1,
                locked_by = :lockedBy,
                locked_at = :now,
                locked_until = :lockedUntil,
                updated_at = :now
            FROM candidates
            WHERE e.id = candidates.id
            RETURNING e.*
            """.trimIndent(),
            OutboxEntryPO::class.java
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
    open override fun markPublished(entry: OutboxEntry, lockedBy: String): Boolean {
        val updated = entityManager.createNativeQuery(
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
            """.trimIndent()
        )
            .setParameter("id", entry.id)
            .setParameter("lockedBy", lockedBy)
            .setParameter("updatedAt", entry.updatedAt)
            .executeUpdate() == 1
        entityManager.clear()
        return updated
    }

    @Transactional
    open override fun markFailed(entry: OutboxEntry, lockedBy: String): Boolean {
        val updated = entityManager.createNativeQuery(
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
            """.trimIndent()
        )
            .setParameter("id", entry.id)
            .setParameter("lockedBy", lockedBy)
            .setParameter("status", entry.status.name)
            .setParameter("retryCount", entry.retryCount)
            .setParameter("nextAttemptAt", entry.nextAttemptAt)
            .setParameter("updatedAt", entry.updatedAt)
            .setParameter("lastError", entry.lastError)
            .executeUpdate() == 1
        entityManager.clear()
        return updated
    }

    @Transactional
    open override fun deletePublishedBefore(before: Instant, batchSize: Int): Int {
        val deleted = entityManager.createNativeQuery(
            """
            DELETE FROM outbox_entry
            WHERE id IN (
                SELECT id
                FROM outbox_entry
                WHERE status = 'PUBLISHED' AND created_at < :before
                ORDER BY created_at ASC
                LIMIT :batchSize
            )
            """.trimIndent()
        )
            .setParameter("before", before)
            .setParameter("batchSize", batchSize)
            .executeUpdate()
        entityManager.clear()
        return deleted
    }

    override fun findDeadLetters(batchSize: Int): List<OutboxEntry> {
        val deadLetters = entityManager.createNativeQuery(
            """
            SELECT *
            FROM outbox_entry
            WHERE status = 'DEAD_LETTER'
            ORDER BY updated_at ASC
            LIMIT :batchSize
            """.trimIndent(),
            OutboxEntryPO::class.java
        )
            .setParameter("batchSize", batchSize)
            .resultList

        @Suppress("UNCHECKED_CAST")
        return (deadLetters as List<OutboxEntryPO>).map(Converter::toDomain)
    }

    @Transactional
    open override fun requeueDeadLetters(ids: Collection<String>, nextAttemptAt: Instant): Int {
        if (ids.isEmpty()) {
            return 0
        }
        val updated = entityManager.createQuery(
            """
            UPDATE OutboxEntryPO e
            SET e.status = :failed,
                e.nextAttemptAt = :nextAttemptAt,
                e.lockedBy = NULL,
                e.lockedAt = NULL,
                e.lockedUntil = NULL,
                e.lastError = NULL,
                e.updatedAt = :now
            WHERE e.status = :deadLetter
              AND e.id IN :ids
            """.trimIndent()
        )
            .setParameter("failed", OutboxEntryStatus.FAILED)
            .setParameter("deadLetter", OutboxEntryStatus.DEAD_LETTER)
            .setParameter("nextAttemptAt", nextAttemptAt)
            .setParameter("now", Instant.now())
            .setParameter("ids", ids)
            .executeUpdate()
        entityManager.clear()
        return updated
    }

    override fun countByStatus(status: OutboxEntryStatus): Long {
        return entityManager.createQuery(
            "SELECT COUNT(e) FROM OutboxEntryPO e WHERE e.status = :status",
            java.lang.Long::class.java
        )
            .setParameter("status", status)
            .singleResult
            .toLong()
    }

    private object Converter {
        fun toPO(entry: OutboxEntry) = OutboxEntryPO(
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
            lastError = entry.lastError
        )

        fun toDomain(po: OutboxEntryPO) = OutboxEntry(
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
            lastError = po.lastError
        )
    }
}
