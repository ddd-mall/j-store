package com.jstore.common.framework.event

import com.jstore.common.framework.messaging.MessageConsumptionRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.PayloadApplicationEvent

class DomainListenerSpringWrapperTest :
    FunSpec({
        class CountingListener : DomainEventListener<StubDomainEvent> {
            var count = 0

            override fun listenerId(): String = "test.counting-listener"

            override fun onDomainEvent(event: StubDomainEvent) {
                count++
            }
        }

        class RecordingConsumptionRepository(private val accepted: Boolean) :
            MessageConsumptionRepository {
            val attempts = mutableListOf<String>()

            override fun tryStart(
                consumerId: String,
                messageId: String,
                messageName: String,
                messageVersion: Int,
            ): Boolean {
                attempts.add("$consumerId:$messageId")
                return accepted
            }
        }

        test("domain listener executes only when idempotency repository accepts the event") {
            val listener = CountingListener()
            val consumptionRepository = RecordingConsumptionRepository(accepted = true)
            val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubDomainEvent()))

            listener.count shouldBe 1
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener is skipped when event was already consumed by the listener") {
            val listener = CountingListener()
            val consumptionRepository = RecordingConsumptionRepository(accepted = false)
            val wrapper = DomainListenerSpringWrapper(listener, consumptionRepository)

            wrapper.onApplicationEvent(PayloadApplicationEvent(this, StubDomainEvent()))

            listener.count shouldBe 0
            consumptionRepository.attempts shouldBe listOf("test.counting-listener:event-1")
        }

        test("domain listener wrapper opts out of async Spring multicaster execution") {
            val wrapper =
                DomainListenerSpringWrapper(
                    CountingListener(),
                    MessageConsumptionRepository { _, _, _, _ -> true },
                )

            wrapper.supportsAsyncExecution() shouldBe false
        }
    })
