package com.jstore.common.framework.event.outbox

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import java.time.Instant
import java.util.UUID

/**
 * 基于 Outbox 模式的事件发布者实现。
 *
 * 将领域事件序列化后写入 Outbox 表（状态为 PENDING），
 * 替代 SpringDomainEventPublisher 的直接内存投递。
 */
class OutboxEventPublisher(
    private val outboxEntryRepository: OutboxEntryRepository,
    private val eventSerializer: EventSerializer,
) : DomainEventPublisher {

    override fun <T : DomainEvent> publishEvent(event: T) {
        val now = Instant.now()
        val entry = OutboxEntry(
            id = UUID.randomUUID().toString(),
            eventType = event::class.java.name,
            payload = eventSerializer.serialize(event),
            aggregateType = extractAggregateType(event),
            aggregateId = extractAggregateId(event),
            status = OutboxEntryStatus.PENDING,
            createdAt = now,
            updatedAt = now,
            retryCount = 0
        )
        outboxEntryRepository.save(entry)
    }

    private fun extractAggregateType(event: DomainEvent): String {
        return event.source::class.java.simpleName
    }

    private fun extractAggregateId(event: DomainEvent): String {
        return event.source.toString()
    }
}
