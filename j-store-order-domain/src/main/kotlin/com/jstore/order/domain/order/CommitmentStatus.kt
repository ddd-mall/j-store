package com.jstore.order.domain.order

/** 订单在跨上下文销售承诺 Saga 中的持久化阶段。 */
enum class CommitmentStatus {
    PENDING_OFFER,
    OFFER_AUTHORIZED,
    CONFIRMED,
    FAILED,
}
