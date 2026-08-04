package com.jstore.translator

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.goods.acl.event.AfterSaleStockRestoreRequestedEvent
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.event.AfterSaleEventItem
import com.jstore.order.domain.aftersale.event.AfterSaleRefundSucceededEvent
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class AfterSaleRefundSucceededToStockRestoreTranslatorTest {
    @Test
    fun `successful refund publishes quantity-aware stock restore`() {
        val publisher = CapturingPublisher()
        val event = AfterSaleRefundSucceededEvent(
            AfterSaleId(1),
            OrderId(2),
            "refund-1",
            listOf(AfterSaleEventItem(OrderItemId(4), 5, 2, Price.ofFen(100), "CNY")),
            Price.ofFen(100),
            "CNY",
            Instant.EPOCH,
        )

        AfterSaleRefundSucceededToStockRestoreTranslator(publisher).onDomainEvent(event)

        val restored = publisher.events.single() as AfterSaleStockRestoreRequestedEvent
        assertEquals(2, restored.items.single().quantity)
    }

    private class CapturingPublisher : DomainEventPublisher {
        val events = mutableListOf<DomainEvent>()
        override fun <T : DomainEvent> publishEvent(event: T) {
            events += event
        }
    }
}
