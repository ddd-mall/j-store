package com.jstore.common.framework.messaging

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SpringLocalIntegrationMessageBusTest :
    FunSpec({
        test("local bus invokes a command handler once after claiming message id") {
            val handled = mutableListOf<Long>()
            val handler =
                object : IntegrationMessageHandler<TestReserveInventoryCommand> {
                    override fun handlerId() = "inventory.reserve.v1"

                    override fun handle(message: TestReserveInventoryCommand) {
                        handled += message.orderId
                    }
                }
            val consumption = mock<MessageConsumptionRepository>()
            whenever(
                    consumption.tryStart(
                        handler.handlerId(),
                        message.messageId,
                        message.messageName,
                        message.messageVersion,
                    )
                )
                .thenReturn(true, false)
            val bus = SpringLocalIntegrationMessageBus(listOf(handler), consumption)

            bus.publish(message)
            bus.publish(message)

            handled shouldBe listOf(42L)
            verify(consumption, org.mockito.kotlin.times(2))
                .tryStart(
                    handler.handlerId(),
                    message.messageId,
                    message.messageName,
                    message.messageVersion,
                )
        }

        test("command delivery requires exactly one owning handler") {
            val bus =
                SpringLocalIntegrationMessageBus(
                    emptyList(),
                    mock<MessageConsumptionRepository>(),
                )

            shouldThrow<IllegalStateException> { bus.publish(message) }
        }
    })
