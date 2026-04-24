package com.jstore.order.domain.order

enum class OrderStatus {
    /** 待支付 — 订单创建后的初始状态 */
    PENDING_PAYMENT,
    /** 已支付 — 买家完成支付 */
    PAID,
    /** 待发货 — 支付确认后，等待卖家发货 */
    PENDING_SHIPMENT,
    /** 已发货 — 卖家已发出商品 */
    SHIPPED,
    /** 已签收 — 买家确认收货 */
    DELIVERED,
    /** 已完成 — 订单正常结束 */
    COMPLETED,
    /** 已取消 — 订单被取消（逆向，预留） */
    CANCELLED,
    /** 退款中 — 订单退款处理中（逆向，预留） */
    REFUNDING
}