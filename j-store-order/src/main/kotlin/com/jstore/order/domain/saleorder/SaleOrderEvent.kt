package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent


class SaleOrderPrepareToCreateEvent(
    val createCMD: SaleOrderCreateCmd,
    source: Any
    ) : DomainEvent(source)

class SaleOrderCreatedEvent(
    val order: SaleOrder,
    source: Any
) : DomainEvent(source)

class FailToCreateSaleOrderEvent(
    val createCMD: SaleOrderCreateCmd,
    source: Any
) : DomainEvent(source)