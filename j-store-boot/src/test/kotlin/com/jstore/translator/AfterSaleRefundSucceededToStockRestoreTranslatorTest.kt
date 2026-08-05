package com.jstore.translator

import com.jstore.common.framework.messaging.IntegrationMessage
import com.jstore.common.framework.messaging.IntegrationMessagePublisher
import com.jstore.common.properties.Price
import com.jstore.contracts.commerce.RestoreInventoryAfterRefundCommand
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.event.AfterSaleEventItem
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class AfterSaleRefundSucceededToStockRestoreTranslatorTest {
    @Test
    fun `successful refund publishes quantity-aware stock restore`() {
        val publisher = CapturingPublisher()
        val event =
            AfterSaleRefundSucceededEvent(
                AfterSaleId(1),
                OrderId(2),
                "refund-1",
                listOf(AfterSaleEventItem(OrderItemId(4), 5, 2, Price.ofFen(100), "CNY")),
                Price.ofFen(100),
                "CNY",
                Instant.EPOCH,
            )

        AfterSaleRefundSucceededToStockRestoreTranslator(publisher).onDomainEvent(event)

        val restored = publisher.messages.single() as RestoreInventoryAfterRefundCommand
        assertEquals(2, restored.items.single().quantity)
    }

    private class CapturingPublisher : IntegrationMessagePublisher {
        val messages = mutableListOf<IntegrationMessage>()

        override fun publish(message: IntegrationMessage) {
            messages += message
        }
    }
}
