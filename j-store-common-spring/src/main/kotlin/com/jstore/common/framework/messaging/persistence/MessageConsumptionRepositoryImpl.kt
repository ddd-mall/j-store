package com.jstore.common.framework.messaging.persistence

import com.jstore.common.framework.messaging.MessageConsumptionRepository
import jakarta.persistence.EntityManager
import java.time.Instant
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

open class MessageConsumptionRepositoryImpl(private val entityManager: EntityManager) :
    MessageConsumptionRepository {

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
}
