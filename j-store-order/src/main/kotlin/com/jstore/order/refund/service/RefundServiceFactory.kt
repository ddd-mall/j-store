package com.jstore.com.jstore.order.refund.service

import com.jstore.com.jstore.order.common.Errors

object RefundServiceFactory {
    private var refundServiceHolder: List<RefundService> = listOf()
    fun setRefundServiceHolder(refundServiceHolder: List<RefundService>) {
        this.refundServiceHolder = refundServiceHolder
    }
    fun getOne(): RefundService {
        if (refundServiceHolder.isEmpty()) {
            throw Errors.Companion.CommonlyErrors.INTERNAL_ERROR.withMsg("没有找到可用的 refund service")
        }
        return refundServiceHolder.first()
    }
}