package com.jstore.fulfillment.service

import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.domain.ShippingRecipient
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FulfillmentApplicationServiceTest {
    @Test
    fun `prepare persists aggregate and publishes its domain event`() {
        val fulfillment =
            FulfillmentOrderImpl(
                FulfillmentOrderId(1),
                orderId = 9,
                merchantId = 7,
                recipient = ShippingRecipient("张三", null, null, "CN", "310000", "测试地址"),
                items = listOf(FulfillmentItem(1, 2, 1)),
            )
        val repository = FakeRepository(fulfillment)
        val published = mutableListOf<DomainEvent>()
        val service =
            FulfillmentApplicationService(
                repository,
                SnowFlakSequence(1, 1),
                object : DomainEventPublisher {
                    override fun publishEvent(event: DomainEvent) {
                        published += event
                    }
                },
            )

        val result = service.prepare(9, Instant.EPOCH)

        assertEquals(true, assertIs<Success<Boolean>>(result).value)
        assertEquals(1, repository.saveCount)
        assertIs<FulfillmentPreparedEvent>(published.single())
    }

    @Test
    fun `prepare propagates not found as a business failure`() {
        val service =
            FulfillmentApplicationService(
                FakeRepository(null),
                SnowFlakSequence(1, 1),
                object : DomainEventPublisher {
                    override fun publishEvent(event: DomainEvent) = Unit
                },
            )

        val result = service.prepare(9, Instant.EPOCH)

        assertEquals(FulfillmentErrors.NOT_FOUND, assertIs<Failure<*>>(result).error)
    }

    private class FakeRepository(initial: FulfillmentOrder?) : FulfillmentOrderRepository {
        private var fulfillment = initial
        var saveCount = 0
            private set

        override fun save(aggregate: FulfillmentOrder): FulfillmentOrder = aggregate.also {
            fulfillment = it
            saveCount++
        }

        override fun findById(id: FulfillmentOrderId) = fulfillment?.takeIf { it.id == id }

        override fun findByOrderId(orderId: Long) = fulfillment?.takeIf { it.orderId == orderId }
    }
}
