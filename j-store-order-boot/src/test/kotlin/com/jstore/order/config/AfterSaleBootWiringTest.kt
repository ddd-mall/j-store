package com.jstore.order.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.order.config.OrderBootConfiguration
import com.jstore.order.domain.aftersale.AfterSaleRepository
import com.jstore.order.domain.order.OrderRepository
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class AfterSaleBootWiringTest {
    @Test
    fun `configuration exposes after-sale factory and application service`() {
        val configuration = OrderBootConfiguration()
        val sequence = configuration.snowFlakSequence()
        val factory = configuration.afterSaleFactory(sequence)
        val repository = mock(AfterSaleRepository::class.java)
        val orders = mock(OrderRepository::class.java)
        assertNotNull(
            configuration.afterSaleApplicationService(
                factory,
                repository,
                orders,
                mock(DomainEventPublisher::class.java),
            )
        )
    }
}
