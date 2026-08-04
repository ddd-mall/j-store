package com.jstore.common.framework.event.outbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class OutboxSchedulerHealthTest : FunSpec({
    val now = Instant.parse("2026-08-04T02:00:00Z")
    val clock = Clock.fixed(now, ZoneOffset.UTC)

    test("scheduler records a successful poll") {
        val publisher = mock<OutboxPublisher>()
        val monitor = RecordingOutboxMonitor()
        val state = SchedulerExecutionState()
        val scheduler = OutboxScheduler(publisher, mock(), monitor, clock, state)

        scheduler.schedulePollAndPublish()

        state.snapshot() shouldBe SchedulerExecutionSnapshot(now, null, 0)
        verify(publisher).pollAndPublish()
    }

    test("scheduler records and rethrows poll failures") {
        val publisher = mock<OutboxPublisher> { on { pollAndPublish() } doThrow IllegalStateException("database unavailable") }
        val monitor = RecordingOutboxMonitor()
        val state = SchedulerExecutionState()
        val scheduler = OutboxScheduler(publisher, mock(), monitor, clock, state)

        shouldThrow<IllegalStateException> { scheduler.schedulePollAndPublish() }

        state.snapshot() shouldBe SchedulerExecutionSnapshot(null, now, 1)
    }
})

private class RecordingOutboxMonitor : OutboxMonitor {
    override fun recordPoll(delivered: Int, failed: Int) = Unit
    override fun recordDeadLetter(entry: OutboxEntry) = Unit
    override fun recordRequeue(count: Int) = Unit
    override fun recordSchedulerSuccess(at: Instant) = Unit
    override fun recordSchedulerFailure(at: Instant) = Unit
}
