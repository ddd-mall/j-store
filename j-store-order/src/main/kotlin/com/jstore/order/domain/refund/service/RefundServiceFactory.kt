package com.jstore.order.domain.refund.service

import com.jstore.common.errors.CommonErrors
import org.springframework.stereotype.Component

object RefundServiceFactory {
    private var refundServiceHolder: List<RefundService> = listOf()

    fun setRefundServiceHolder(refundServiceHolder: List<RefundService>) {
        RefundServiceFactory.refundServiceHolder = refundServiceHolder
    }
    fun getOne(): RefundService {
        if (refundServiceHolder.isEmpty()) {
            throw CommonErrors.INTERNAL_ERROR.to("没有找到可用的 refund service")
        }
        return refundServiceHolder.first()
    }
}

@Component
class RefundServiceFactoryInitailizer(refundServiceMap: Map<String, RefundService>) {
    init {
        RefundServiceFactory.setRefundServiceHolder(refundServiceMap.values.toList())

    }
}