package com.jstore.order.config

import com.jstore.com.jstore.order.config.OrderBootConfiguration
import com.jstore.order.acl.AfterSaleMerchantResolver
import com.jstore.order.domain.aftersale.AfterSaleRepository
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.service.OrderRefundProjectionService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import kotlin.test.assertNotNull

class AfterSaleBootWiringTest {
    @Test
    fun `configuration exposes after-sale factory service resolver and projection handler`() {
        val configuration = OrderBootConfiguration()
        val sequence = configuration.snowFlakSequence()
        val factory = configuration.afterSaleFactory(sequence)
        val resolver = configuration.afterSaleMerchantResolver(OrderMerchantProperties(7))
        val repository = mock(AfterSaleRepository::class.java)
        val orders = mock(OrderRepository::class.java)
        assertNotNull(configuration.afterSaleApplicationService(factory, repository, orders, resolver))
        assertNotNull(configuration.orderRefundProjectionHandler(mock(OrderRefundProjectionService::class.java)))
        assertNotNull(resolver as AfterSaleMerchantResolver)
    }
}
