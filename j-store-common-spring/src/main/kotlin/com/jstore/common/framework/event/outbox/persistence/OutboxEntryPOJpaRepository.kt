package com.jstore.common.framework.event.outbox.persistence

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface OutboxEntryPOJpaRepository : JpaRepository<OutboxEntryPO, String> {

    @Query(
        """
        SELECT e FROM OutboxEntryPO e
        WHERE e.status = 'PENDING'
           OR (e.status = 'FAILED' AND e.retryCount < :maxRetryCount AND e.nextAttemptAt <= :now)
        ORDER BY e.createdAt ASC
        """
    )
    fun findPendingAndRetryable(
        @Param("maxRetryCount") maxRetryCount: Int,
        @Param("now") now: Instant,
        pageable: Pageable
    ): List<OutboxEntryPO>

}
