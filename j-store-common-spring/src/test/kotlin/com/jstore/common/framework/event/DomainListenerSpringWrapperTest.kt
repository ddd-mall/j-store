package com.jstore.common.framework.event

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.PayloadApplicationEvent

class DomainListenerSpringWrapperTest : FunSpec({

    data class StubEvent(
        override val source: Any = "source",
        override val eventId: String = "event-1",
        override val eventName: String = "test.stub-event",
        override val eventVersion: Int = 1,
        override val occurredAt: java.time.Instant = java.time.Instant.parse("2025-01-01T00:00:00Z"),
        override val aggregateType: String = "Test",
        override val aggregateId: String = "1",
    ) : ExplicitDomainEvent

    class CountingListener : DomainEventListener<StubEvent> {
        var count = 0

        override fun listenerId(): String = "test.counting-listener"

        override fun onDomainEvent(event: StubEvent) {
            count++
        }
    }

    class RecordingConsumptionRepository(
        private val accepted: Boolean,
    ) : DomainEventConsumptionRepository {
        val attempts = mutableListOf<String>()

        override fun tryStart(listenerId: String, event: DomainEvent): Boolean {
            attempts.add("$listenerId:${event.metadata.eventId}")
            return accepted
        }
    }

    test("domain listener executes only when idempotency repository accepts the event") {
        val listener = CountingListener()
        val consumptionRepository = RecordingConsumptionRepository(accepted = true)
        val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

        wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubEvent()))

        listener.count shouldBe 1
        consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
    }

    test("domain listener is skipped when event was already consumed by the listener") {
        val listener = CountingListener()
        val consumptionRepository = RecordingConsumptionRepository(accepted = false)
        val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

        wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubEvent()))

        listener.count shouldBe 0
        consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
    }

    test("domain listener wrapper opts out of async Spring multicaster execution") {
        val wrapper = DomainListenerSpringWrapper(CountingListener())

        wrapper.supportsAsyncExecution() shouldBe false
    }
})
