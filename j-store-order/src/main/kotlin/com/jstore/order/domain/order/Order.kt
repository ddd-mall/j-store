package com.jstore.order.domain.order

import com.jstore.common.framework.AgreeGate

interface Order : AgreeGate<OrderId> {

    override val id: OrderId

    fun reserve(): Order

    fun pay(): Order

    fun shipping(): Order

    fun complete(): Order

    fun cancel(): Order

    fun refund(): Order

    fun confirm(): Order

    fun undo(): Order


}