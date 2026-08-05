package com.jstore.common.framework.event.outbox

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import java.time.Instant

interface OutboxMonitor {
    fun recordPoll(delivered: Int, failed: Int)

    fun recordDeadLetter(entry: OutboxEntry)

    fun recordRequeue(count: Int)

    fun recordSchedulerSuccess(at: Instant) {}

    fun recordSchedulerFailure(at: Instant) {}
}

object NoopOutboxMonitor : OutboxMonitor {
    override fun recordPoll(delivered: Int, failed: Int) {}

    override fun recordDeadLetter(entry: OutboxEntry) {}

    override fun recordRequeue(count: Int) {}

    override fun recordSchedulerSuccess(at: Instant) {}

    override fun recordSchedulerFailure(at: Instant) {}
}

class MicrometerOutboxMonitor(
    private val meterRegistry: MeterRegistry,
    private val outboxEntryRepository: OutboxEntryRepository,
    private val operationalHealth: OutboxOperationalHealth,
    private val schedulerState: SchedulerExecutionState,
) : OutboxMonitor {

    init {
        OutboxEntryStatus.entries.forEach { status ->
            meterRegistry.gauge(
                "jstore.outbox.entries",
                listOf(Tag.of("status", status.name)),
                status,
            ) {
                outboxEntryRepository.countByStatus(it).toDouble()
            }
        }

        meterRegistry.gauge(
            "jstore.outbox.oldest_ready.lag",
            operationalHealth,
        ) {
            it.snapshot().oldestReadyLag.toMillis() / 1000.0
        }

        meterRegistry.gauge(
            "jstore.outbox.expired_locks",
            operationalHealth,
        ) {
            it.snapshot().expiredLockCount.toDouble()
        }

        registerAlertGauge("lag") { it.lagAlert }
        registerAlertGauge("expired_lock") { it.expiredLockAlert }
        registerAlertGauge("dead_letter") { it.deadLetterAlert }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.last_success",
            schedulerState,
        ) {
            it.snapshot().lastSuccessAt?.epochSecond?.toDouble() ?: 0.0
        }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.last_failure",
            schedulerState,
        ) {
            it.snapshot().lastFailureAt?.epochSecond?.toDouble() ?: 0.0
        }

        meterRegistry.gauge(
            "jstore.outbox.scheduler.consecutive_failures",
            schedulerState,
        ) {
            it.snapshot().consecutiveFailures.toDouble()
        }
    }

    override fun recordPoll(delivered: Int, failed: Int) {
        meterRegistry.counter("jstore.outbox.delivered").increment(delivered.toDouble())
        meterRegistry.counter("jstore.outbox.failed").increment(failed.toDouble())
    }

    override fun recordDeadLetter(entry: OutboxEntry) {
        meterRegistry
            .counter(
                "jstore.outbox.dead_letter",
                "eventType",
                entry.eventType,
                "aggregateType",
                entry.aggregateType,
            )
            .increment()
    }

    override fun recordRequeue(count: Int) {
        meterRegistry.counter("jstore.outbox.dead_letter.requeued").increment(count.toDouble())
    }

    override fun recordSchedulerSuccess(at: Instant) {
        meterRegistry.counter("jstore.outbox.scheduler.success").increment()
    }

    override fun recordSchedulerFailure(at: Instant) {
        meterRegistry.counter("jstore.outbox.scheduler.failure").increment()
    }

    private fun registerAlertGauge(reason: String, active: (OutboxOperationalSnapshot) -> Boolean) {
        meterRegistry.gauge(
            "jstore.outbox.alert",
            listOf(Tag.of("reason", reason)),
            operationalHealth,
        ) {
            if (active(it.snapshot())) 1.0 else 0.0
        }
    }
}
