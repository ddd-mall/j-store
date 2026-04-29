package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
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

    private object Converter {
        fun toPO(entry: OutboxEntry) = OutboxEntryPO(
            id = entry.id,
            eventType = entry.eventType,
            payload = entry.payload,
            aggregateType = entry.aggregateType,
            aggregateId = entry.aggregateId,
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
            eventType = po.eventType,
            payload = po.payload,
            aggregateType = po.aggregateType,
            aggregateId = po.aggregateId,
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
