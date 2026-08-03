package com.jstore.order.domain.aftersale

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.order.domain.aftersale.persistence.*
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemId
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDateTime
import java.util.LinkedList
import kotlin.test.assertEquals

class AfterSalePORoundTripTest {
    @Test
    fun `persistence mapping preserves aggregate snapshots and review decision`() {
        val repository = AfterSaleRepositoryImpl(
            mock(AfterSalePOJpaRepository::class.java), mock(AfterSaleCapacityPOJpaRepository::class.java),
            mock(AfterSaleCommandReceiptPOJpaRepository::class.java), mock(DomainEventPublisher::class.java),
            SnowFlakSequence(1, 1), mock(PlatformTransactionManager::class.java)
        )
        val now = LocalDateTime.of(2026, 8, 3, 10, 0)
        val goods = GoodsSnapshot(91, 81, "商品", "红色")
        val source = AfterSaleImpl(
            AfterSaleId(1), OrderId(2), ApplicantActorId(3), MerchantActorId(4), AfterSaleStatus.REJECTED,
            RefundReason(RefundCategory.OTHER, "不合适"), FulfillmentSnapshot(FulfillmentStatus.DELIVERED, true),
            listOf(AfterSaleItemImpl(AfterSaleItemId(5), OrderId(2), OrderItemId(6), 2, Price.ofFen(120), "CNY", RefundEligibilitySnapshot(OrderItemId(6), 3, Price.ofFen(200), "CNY", goods))),
            ReviewDecision(MerchantActorId(4), now, "已使用"), null, now.minusDays(1), now, 7, LinkedList()
        )

        val restored = repository.toDomain(repository.toPO(source))

        assertEquals(source.id, restored.id); assertEquals(source.orderId, restored.orderId)
        assertEquals(source.status, restored.status); assertEquals(source.reason, restored.reason)
        assertEquals(source.fulfillmentSnapshot, restored.fulfillmentSnapshot)
        assertEquals(source.reviewDecision, restored.reviewDecision); assertEquals(source.items, restored.items)
        assertEquals(source.version, restored.version)
    }
}
