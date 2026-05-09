package com.jstore.common.framework.event

/**
 * 事务性领域事件发布者。
 *
 * 默认生产实现写入 transactional outbox，应与业务数据处于同一数据库事务中，
 * 不负责本进程监听器分发。
 */
interface DomainEventPublisher {
    fun <T: DomainEvent> publishEvent(event: T)
}
