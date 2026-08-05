package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.PaymentStatus
import com.jstore.order.domain.order.TradeStatus
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.testOrder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class OrderServiceStatusDimensionsTest :
    FunSpec({
        test("domain failure is propagated and aggregate is not saved") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val publisher = mock(DomainEventPublisher::class.java)
            val order = mock(Order::class.java)
            val id = OrderId(1)
            `when`(repository.findById(id)).thenReturn(order)
            `when`(order.confirmStock()).thenReturn(Failure(OrderErrors.ILLEGAL_STATE))

            OrderService(factory, repository, publisher)
                .confirmStock(id)
                .shouldBeInstanceOf<Failure<*>>()
            verify(repository, never()).save(order)
        }

        test("completing an order persists and publishes OrderCompletedEvent") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val order =
                testOrder(
                    trade = TradeStatus.ACTIVE,
                    payment = PaymentStatus.PAID,
                    fulfillment = FulfillmentStatus.DELIVERED,
                    itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED),
                )
            val published = mutableListOf<com.jstore.common.framework.event.DomainEvent>()
            val publisher =
                object : DomainEventPublisher {
                    override fun <T : com.jstore.common.framework.event.DomainEvent> publishEvent(
                        event: T
                    ) {
                        published += event
                    }
                }
            `when`(repository.findById(order.id)).thenReturn(order)
            `when`(repository.save(order)).thenReturn(order)

            OrderService(factory, repository, publisher).completeOrder(order.id) shouldBe
                Success(Unit)

            published.single().shouldBeInstanceOf<OrderCompletedEvent>()
            order.domainEventQueue.size shouldBe 0
        }
    })
