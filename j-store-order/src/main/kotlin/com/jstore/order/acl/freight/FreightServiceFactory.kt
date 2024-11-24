package com.jstore.com.jstore.order.acl.freight

import com.jstore.com.jstore.order.common.Errors

object FreightServiceFactory {
    private var services: MutableList<FreightSerivce> = ArrayList();
    fun setServices(services: MutableList<FreightSerivce>) {
        this.services = services
    }

    fun getAny(): FreightSerivce {
        if (services.isNotEmpty()) {
            return services.first()
        }
        throw Errors.Companion.CommonlyErrors.INTERNAL_ERROR.withMsg("没有找到可用的RefundService")
    }
}