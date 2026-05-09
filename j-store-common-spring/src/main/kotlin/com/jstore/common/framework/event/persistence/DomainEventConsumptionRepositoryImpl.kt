package com.jstore.common.framework.event.persistence

import com.jstore.common.framework.event.DomainEventConsumptionRepository
import com.jstore.common.framework.event.DomainEvent
import jakarta.persistence.EntityManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

open class DomainEventConsumptionRepositoryImpl(
    private val entityManager: EntityManager,
) : DomainEventConsumptionRepository {

    @Transactional(propagation = Propagation.MANDATORY)
    open override fun tryStart(listenerId: String, event: DomainEvent): Boolean {
        val metadata = event.metadata
        val inserted = entityManager.createNativeQuery(
            """
            INSERT INTO domain_event_consumption (listener_id, event_id, event_name, event_version, consumed_at)
            VALUES (:listenerId, :eventId, :eventName, :eventVersion, :consumedAt)
            ON CONFLICT (listener_id, event_id) DO NOTHING
            """.trimIndent()
        )
            .setParameter("listenerId", listenerId)
            .setParameter("eventId", metadata.eventId)
            .setParameter("eventName", metadata.eventName)
            .setParameter("eventVersion", metadata.eventVersion)
            .setParameter("consumedAt", Instant.now())
            .executeUpdate()
        return inserted == 1
    }
}
