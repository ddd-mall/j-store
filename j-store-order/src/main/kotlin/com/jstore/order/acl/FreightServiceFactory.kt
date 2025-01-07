package com.jstore.order.acl

import com.jstore.common.errors.CommonErrors

object FreightServiceFactory {
    private var services: MutableList<FreightService> = ArrayList()
    fun setServices(services: MutableList<FreightService>) {
        FreightServiceFactory.services = services
    }

    fun getAny(): FreightService {
        if (services.isNotEmpty()) {
            return services.first()
        }
        throw CommonErrors.INTERNAL_ERROR.to("没有找到可用的RefundService")
    }
}