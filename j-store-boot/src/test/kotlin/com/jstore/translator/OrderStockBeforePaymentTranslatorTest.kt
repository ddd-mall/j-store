package com.jstore.translator

import com.jstore.common.framework.messaging.IntegrationMessage
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.common.properties.Price
import com.jstore.contracts.commerce.CreatePaymentForOrderCommand
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test

class OrderStockBeforePaymentTranslatorTest {
    @Test
    fun `stock-confirmed order creates payment command`() {
        val publisher = CapturingPublisher()
        val event =
            OrderStockConfirmedEvent(
                orderId = OrderId(1),
                merchantId = MerchantId(7),
                payableAmount = Price.ofFen(200),
                currency = "CNY",
                occurredAt = Instant.EPOCH,
                eventId = "stock-confirmed-1",
            )

        OrderStockConfirmedToPaymentTranslator(publisher).onDomainEvent(event)

        val command = assertIs<CreatePaymentForOrderCommand>(publisher.messages.single())
        assertEquals(1, command.orderId)
        assertEquals(7, command.merchantId)
        assertEquals(200, command.payableAmountFen)
        assertEquals("CNY", command.currency)
        assertEquals("stock-confirmed-1", command.sourceMessageId)
        assertEquals(Instant.EPOCH, command.occurredAtValue)
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
