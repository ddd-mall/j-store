package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEvent


data class SaleOrderPrepareToCreateEvent(
    val createCMD: NormalSaleOrderCreateCmd,

    ) : DomainEvent(createCMD)

data class SaleOrderCreatedEvent(
    val order: SaleOrder,
) : DomainEvent(order)

data class FailToCreateSaleOrderEvent(
    val createCMD: NormalSaleOrderCreateCmd,
) : DomainEvent(createCMD)