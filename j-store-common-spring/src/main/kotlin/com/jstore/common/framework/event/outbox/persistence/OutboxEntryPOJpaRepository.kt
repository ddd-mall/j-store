package com.jstore.common.framework.event.outbox.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxEntryPOJpaRepository : JpaRepository<OutboxEntryPO, String> {

    @Query(
        """
        SELECT e FROM OutboxEntryPO e
        WHERE e.status = 'PENDING'
           OR (e.status = 'FAILED' AND e.retryCount < :maxRetryCount)
        ORDER BY e.createdAt ASC
        """
    )
    fun findPendingAndRetryable(
        @Param("maxRetryCount") maxRetryCount: Int,
        pageable: Pageable
    ): List<OutboxEntryPO>

    @Modifying
    @Query(
        """
        DELETE FROM OutboxEntryPO e
        WHERE e.status = 'PUBLISHED' AND e.createdAt < :before
        """
    )
    fun deletePublishedBefore(@Param("before") before: Instant): Int
}
