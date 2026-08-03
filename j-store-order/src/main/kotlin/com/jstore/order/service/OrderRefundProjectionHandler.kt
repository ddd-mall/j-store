package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.order.domain.aftersale.event.AfterSaleApprovedEvent
import com.jstore.order.domain.order.ApprovedRefundItem

interface OrderRefundProjectionService { fun project(event: AfterSaleApprovedEvent) }
class OrderRefundProjectionHandler(private val service:OrderRefundProjectionService):DomainEventListener<AfterSaleApprovedEvent>{
    override fun listenerId()="order.after-sale-approved.refund-projection.v1"
    override fun onDomainEvent(event:AfterSaleApprovedEvent)=service.project(event)
}
class NonRetryableRefundProjectionException(message:String):RuntimeException(message)
