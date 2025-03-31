package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEvent


class SaleOrderPrepareToCreateEvent(
    val createCMD: SaleOrderCreateCmd,
    override val source: Any,
    ) : DomainEvent

class SaleOrderCreatedEvent(
    val order: SaleOrder,
    override val source: Any,
) : DomainEvent

class FailToCreateSaleOrderEvent(
    val createCMD: SaleOrderCreateCmd,
    override val source: Any,
) : DomainEvent