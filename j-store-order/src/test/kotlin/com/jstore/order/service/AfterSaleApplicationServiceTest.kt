package com.jstore.order.service

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.acl.AfterSaleMerchantResolver
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.command.AfterSaleCreateCMD
import com.jstore.order.domain.aftersale.command.AfterSaleItemRequestCMD
import com.jstore.order.domain.order.*
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class AfterSaleApplicationServiceTest {
    private val factory = mock(AfterSaleFactory::class.java)
    private val afterSales = mock(AfterSaleRepository::class.java)
    private val orders = mock(OrderRepository::class.java)
    private val merchants = mock(AfterSaleMerchantResolver::class.java)
    private val service = AfterSaleApplicationService(factory, afterSales, orders, merchants)

    @Test
    fun `create rejects a non-owner before resolving merchant or saving`() {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID)
        `when`(orders.findById(OrderId(1))).thenReturn(order)

        val result = service.create(command(applicant = 99))

        assertEquals(AfterSaleErrors.APPLICANT_FORBIDDEN, assertIs<Failure<*>>(result).error)
        verifyNoInteractions(merchants, factory)
    }

    @Test
    fun `create propagates factory failure without reserving capacity`() {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.UNPAID)
        `when`(orders.findById(OrderId(1))).thenReturn(order)
        `when`(merchants.merchantFor(order)).thenReturn(Success(MerchantActorId(7)))
        val sequence = mock(com.jstore.common.persistent.SnowFlakSequence::class.java)
        val concreteService = AfterSaleApplicationService(AfterSaleFactoryImpl(sequence), afterSales, orders, merchants)

        val result = concreteService.create(command())

        assertEquals(AfterSaleErrors.ORDER_NOT_ELIGIBLE, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `same command receipt returns stored aggregate without loading order`() {
        val aggregate = mock(AfterSale::class.java)
        val receipt = AfterSaleCommandReceipt(1, AfterSaleCommandType.CREATE, "key", commandHash(command()), AfterSaleId(8), AfterSaleStatus.REQUESTED, java.time.LocalDateTime.MIN)
        `when`(afterSales.findReceipt(1, AfterSaleCommandType.CREATE, "key")).thenReturn(receipt)
        `when`(afterSales.findById(AfterSaleId(8))).thenReturn(aggregate)

        val result = service.create(command())

        assertEquals(aggregate, assertIs<Success<AfterSale>>(result).value)
        verifyNoInteractions(orders, merchants, factory)
    }

    @Test
    fun `single-line request on multi-line order passes only requested ceiling`() {
        val order = testOrder(trade = TradeStatus.ACTIVE, payment = PaymentStatus.PAID, itemStatuses = listOf(OrderItemStatus.NONE, OrderItemStatus.NONE))
        var captured: List<RefundCapacityCeiling>? = null
        val repository = object : AfterSaleRepository {
            override fun createWithAllocation(afterSale: AfterSale, ceilings: List<RefundCapacityCeiling>, receipt: AfterSaleCommandReceipt) = Success(afterSale).also { captured = ceilings }
            override fun findByOrderId(orderId: OrderId) = emptyList<AfterSale>()
            override fun saveDecision(afterSale: AfterSale, allocationAction: AllocationAction, receipt: AfterSaleCommandReceipt) = Success(afterSale)
            override fun findReceipt(actorId: Long, type: AfterSaleCommandType, key: String) = null
            override fun save(entity: AfterSale) = entity
            override fun findById(id: AfterSaleId): AfterSale? = null
        }
        val orderRepository = object : OrderRepository {
            override fun findById(id: OrderId) = order
            override fun add(order: Order) = Unit
            override fun save(entity: Order) = entity
            override fun findByBuyerUserId(uid: Long) = emptyList<Order>()
            override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): com.jstore.common.framework.Page<Order> = throw UnsupportedOperationException()
        }
        val resolver = object : AfterSaleMerchantResolver {
            override fun merchantFor(order: Order) = Success(MerchantActorId(7))
        }
        val actual = AfterSaleApplicationService(AfterSaleFactoryImpl(com.jstore.common.persistent.SnowFlakSequence(1, 1)), repository, orderRepository, resolver)
            .create(command())

        assertIs<Success<AfterSale>>(actual)
        assertEquals(listOf(OrderItemId(1)), assertNotNull(captured).map { it.orderItemId })
    }

    private fun command(applicant: Long = 1) = AfterSaleCreateCMD(
        OrderId(1), ApplicantActorId(applicant), RefundReason(RefundCategory.OTHER, "reason"),
        listOf(AfterSaleItemRequestCMD(OrderItemId(1), 1, Price.ofFen(100), "CNY")), "key"
    )

    private fun commandHash(command: AfterSaleCreateCMD): String = java.security.MessageDigest.getInstance("SHA-256")
        .digest(command.toString().toByteArray()).joinToString("") { "%02x".format(it) }
}
