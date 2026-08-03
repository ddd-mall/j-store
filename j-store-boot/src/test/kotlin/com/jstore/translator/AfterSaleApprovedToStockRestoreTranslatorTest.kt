package com.jstore.translator
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.goods.acl.event.AfterSaleStockRestoreRequestedEvent
import com.jstore.order.domain.aftersale.*
import com.jstore.order.domain.aftersale.event.*
import com.jstore.order.domain.order.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
class AfterSaleApprovedToStockRestoreTranslatorTest{
 private fun event(returnRequired:Boolean)=AfterSaleApprovedEvent(AfterSaleId(1),OrderId(2),MerchantActorId(3),listOf(AfterSaleEventItem(OrderItemId(4),5,2,Price.ofFen(100),"CNY")),returnRequired,Instant.EPOCH)
 @Test fun `non-return refund publishes quantity-aware restore`(){val publisher=CapturingPublisher();AfterSaleApprovedToStockRestoreTranslator(publisher).onDomainEvent(event(false));val restored=publisher.events.single() as AfterSaleStockRestoreRequestedEvent;assertEquals(2,restored.items.single().quantity)}
 @Test fun `return-required refund does not restore before receipt`(){val publisher=CapturingPublisher();AfterSaleApprovedToStockRestoreTranslator(publisher).onDomainEvent(event(true));assertEquals(0,publisher.events.size)}
 private class CapturingPublisher:DomainEventPublisher{val events=mutableListOf<com.jstore.common.framework.event.DomainEvent>();override fun <T:com.jstore.common.framework.event.DomainEvent> publishEvent(event:T){events+=event}}
}
