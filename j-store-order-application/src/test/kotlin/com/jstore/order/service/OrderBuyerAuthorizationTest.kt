package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.utils.Failure
import com.jstore.order.acl.UserService
import com.jstore.order.domain.order.CancellationCategory
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.command.OrderCancelCMD
import com.jstore.order.domain.order.testOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OrderBuyerAuthorizationTest {
    private val factory = mock<OrderFactory>()
    private val repository = mock<OrderRepository>()
    private val publisher = mock<DomainEventPublisher>()
    private val users = mock<UserService>()
    private val service = OrderService(factory, repository, publisher, users)
    private val anotherBuyersOrder = testOrder()

    @Test
    fun `buyer cannot read another buyers order`() {
        whenever(repository.findById(anotherBuyersOrder.id)).thenReturn(anotherBuyersOrder)

        val result = service.getOrderById(42, anotherBuyersOrder.id)

        assertEquals(Failure(OrderErrors.ORDER_NOT_FOUND), result)
    }

    @Test
    fun `buyer cannot cancel another buyers order`() {
        whenever(repository.findById(anotherBuyersOrder.id)).thenReturn(anotherBuyersOrder)
        val command =
            OrderCancelCMD(
                anotherBuyersOrder.id,
                CancellationCategory.BUYER_CANCELLED,
                "not mine",
            )

        val result = service.cancelOrder(42, command)

        assertEquals(Failure(OrderErrors.ORDER_NOT_FOUND), result)
        verify(repository, never()).save(anotherBuyersOrder)
    }
}
