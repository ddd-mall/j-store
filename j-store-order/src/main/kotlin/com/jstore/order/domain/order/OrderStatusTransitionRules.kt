package com.jstore.order.domain.order

/**
 * 订单状态转移规则
 * 定义了所有合法的状态转移，确保业务一致性
 */
object OrderStatusTransitionRules {

    /**
     * 定义合法的状态转移
     * 正向流程: PENDING_PAYMENT → PAID → PENDING_SHIPMENT → SHIPPED → DELIVERED → COMPLETED
     * 逆向分支: 各状态可转入 CANCELLED 或 REFUNDING（预留）
     */
    private val validTransitions = mapOf(
        OrderStatus.PENDING_PAYMENT to setOf(OrderStatus.PAID, OrderStatus.CANCELLED),
        OrderStatus.PAID to setOf(OrderStatus.PENDING_SHIPMENT, OrderStatus.REFUNDING),
        OrderStatus.PENDING_SHIPMENT to setOf(OrderStatus.SHIPPED, OrderStatus.REFUNDING),
        OrderStatus.SHIPPED to setOf(OrderStatus.DELIVERED),
        OrderStatus.DELIVERED to setOf(OrderStatus.COMPLETED, OrderStatus.REFUNDING),
        // COMPLETED 和 CANCELLED 是终态，没有后续转移
    )

    /**
     * 判断状态转移是否合法
     */
    fun isValidTransition(from: OrderStatus, to: OrderStatus): Boolean {
        return validTransitions[from]?.contains(to) ?: false
    }

    /**
     * 获取当前状态的合法后续状态
     */
    fun getNextStates(current: OrderStatus): Set<OrderStatus> {
        return validTransitions[current] ?: emptySet()
    }

    /**
     * 验证状态转移，如果非法则抛出异常
     */
    fun validateTransition(from: OrderStatus, to: OrderStatus) {
        require(isValidTransition(from, to)) {
            "无法从${from.name}转移到${to.name}"
        }
    }
}

