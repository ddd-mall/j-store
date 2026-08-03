package com.jstore.goods.acl.event

import com.jstore.common.framework.event.ExplicitDomainEvent
import com.jstore.common.framework.event.outbox.DomainEventType
import java.time.Instant
import java.util.UUID

data class StockRestoreItem(val skuId:Long,val quantity:Int)
@DomainEventType(name="stock.after-sale-restore-requested",version=1)
data class AfterSaleStockRestoreRequestedEvent(val afterSaleId:Long,val orderId:Long,val items:List<StockRestoreItem>,override val occurredAt:Instant=Instant.now(),override val eventId:String=UUID.randomUUID().toString()):ExplicitDomainEvent{
 override val source:Any get()=afterSaleId;override val eventName="stock.after-sale-restore-requested";override val eventVersion=1;override val aggregateType="Inventory";override val aggregateId=afterSaleId.toString()
}
