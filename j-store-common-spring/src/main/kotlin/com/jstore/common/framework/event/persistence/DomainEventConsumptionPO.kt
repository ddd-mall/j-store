package com.jstore.common.framework.event.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

data class DomainEventConsumptionId(
    var listenerId: String = "",
    var eventId: String = "",
) : Serializable

@Entity
@IdClass(DomainEventConsumptionId::class)
@Table(name = "domain_event_consumption")
class DomainEventConsumptionPO(
    @Id @Column(name = "listener_id", nullable = false, length = 512) var listenerId: String = "",
    @Id @Column(name = "event_id", nullable = false, length = 64) var eventId: String = "",
    @Column(name = "event_name", nullable = false, length = 256) var eventName: String = "",
    @Column(name = "event_version", nullable = false) var eventVersion: Int = 1,
    @Column(name = "consumed_at", nullable = false) var consumedAt: Instant = Instant.now(),
)
