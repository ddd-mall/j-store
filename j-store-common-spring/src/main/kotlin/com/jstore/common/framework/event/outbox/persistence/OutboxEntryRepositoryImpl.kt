package com.jstore.common.framework.event.outbox.persistence

import com.jstore.common.framework.event.outbox.OutboxEntry
import com.jstore.common.framework.event.outbox.OutboxEntryRepository
import org.springframework.data.domain.PageRequest
import java.time.Instant

class OutboxEntryRepositoryImpl(
    private val jpaRepository: OutboxEntryPOJpaRepository
) : OutboxEntryRepository {

    override fun save(entry: OutboxEntry): OutboxEntry {
        val po = Converter.toPO(entry)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findPendingAndRetryable(maxRetryCount: Int, batchSize: Int): List<OutboxEntry> {
        return jpaRepository.findPendingAndRetryable(
            maxRetryCount, PageRequest.of(0, batchSize)
        ).map(Converter::toDomain)
    }

    override fun deletePublishedBefore(before: Instant, batchSize: Int): Int {
        return jpaRepository.deletePublishedBefore(before)
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
            retryCount = entry.retryCount
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
            retryCount = po.retryCount
        )
    }
}
