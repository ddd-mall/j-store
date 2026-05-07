package com.jstore.common.framework.event.outbox

import io.micrometer.core.instrument.MeterRegistry

interface OutboxMonitor {
    fun recordPoll(delivered: Int, failed: Int)
    fun recordDeadLetter(entry: OutboxEntry)
    fun recordRequeue(count: Int)
}

object NoopOutboxMonitor : OutboxMonitor {
    override fun recordPoll(delivered: Int, failed: Int) {}
    override fun recordDeadLetter(entry: OutboxEntry) {}
    override fun recordRequeue(count: Int) {}
}

class MicrometerOutboxMonitor(
    private val meterRegistry: MeterRegistry,
    private val outboxEntryRepository: OutboxEntryRepository,
) : OutboxMonitor {

    init {
        OutboxEntryStatus.entries.forEach { status ->
            meterRegistry.gauge("jstore.outbox.entries", listOf(io.micrometer.core.instrument.Tag.of("status", status.name)), status) {
                outboxEntryRepository.countByStatus(it).toDouble()
            }
        }
    }

    override fun recordPoll(delivered: Int, failed: Int) {
        meterRegistry.counter("jstore.outbox.delivered").increment(delivered.toDouble())
        meterRegistry.counter("jstore.outbox.failed").increment(failed.toDouble())
    }

    override fun recordDeadLetter(entry: OutboxEntry) {
        meterRegistry.counter(
            "jstore.outbox.dead_letter",
            "eventType", entry.eventType,
            "aggregateType", entry.aggregateType,
        ).increment()
    }

    override fun recordRequeue(count: Int) {
        meterRegistry.counter("jstore.outbox.dead_letter.requeued").increment(count.toDouble())
    }
}
