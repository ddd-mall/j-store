package com.jstore.order.refund

import com.jstore.common.framework.Repository

@org.springframework.stereotype.Repository
interface RefundOrderRepository: Repository<RefundOrderId, RefundOrder>