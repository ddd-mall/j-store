package com.jstore.common.framework.event.outbox

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class OutboxOperationalHealthTest : FunSpec({
    val now = Instant.parse("2026-08-04T02:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)

    test("health distinguishes never-run healthy degraded and failed") {
        val repository = mock<OutboxEntryRepository>()
        whenever(repository.findOldestReadyAt(now, 5)).thenReturn(null)
        whenever(repository.countExpiredLocks(now)).thenReturn(0)
        whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER)).thenReturn(0)
        val state = SchedulerExecutionState()
        val health = OutboxOperationalHealth(repository, state, OutboxObservabilityProperties(), 5, clock)

        health.snapshot().status shouldBe OutboxOperationalStatus.NOT_RUN
        state.recordSuccess(now)
        health.snapshot().status shouldBe OutboxOperationalStatus.HEALTHY

        whenever(repository.findOldestReadyAt(now, 5)).thenReturn(now.minusSeconds(301))
        health.snapshot().status shouldBe OutboxOperationalStatus.DEGRADED

        repeat(3) { state.recordFailure(now) }
        health.snapshot().status shouldBe OutboxOperationalStatus.FAILED
    }

    test("micrometer exposes lag expired-lock scheduler and alert gauges") {
        val repository = mock<OutboxEntryRepository>()
        whenever(repository.findOldestReadyAt(now, 5)).thenReturn(now.minusSeconds(120))
        whenever(repository.countExpiredLocks(now)).thenReturn(2)
        whenever(repository.countByStatus(OutboxEntryStatus.DEAD_LETTER)).thenReturn(4)
        OutboxEntryStatus.entries.forEach { whenever(repository.countByStatus(it)).thenReturn(if (it == OutboxEntryStatus.DEAD_LETTER) 4 else 0) }
        val registry = SimpleMeterRegistry()
        val state = SchedulerExecutionState().apply { recordSuccess(now.minusSeconds(10)) }
        val health = OutboxOperationalHealth(
            repository,
            state,
            OutboxObservabilityProperties(Duration.ofSeconds(60), 1, 3, 3),
            5,
            clock,
        )

        MicrometerOutboxMonitor(registry, repository, health, state)

        registry.get("jstore.outbox.oldest_ready.lag").gauge().value().shouldBeExactly(120.0)
        registry.get("jstore.outbox.expired_locks").gauge().value().shouldBeExactly(2.0)
        registry.get("jstore.outbox.alert").tag("reason", "lag").gauge().value().shouldBeExactly(1.0)
        registry.get("jstore.outbox.alert").tag("reason", "expired_lock").gauge().value().shouldBeExactly(1.0)
        registry.get("jstore.outbox.alert").tag("reason", "dead_letter").gauge().value().shouldBeExactly(1.0)
        registry.get("jstore.outbox.scheduler.last_success").gauge().value().shouldBeExactly(now.minusSeconds(10).epochSecond.toDouble())
    }
})
