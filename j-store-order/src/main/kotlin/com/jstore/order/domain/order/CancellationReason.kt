package com.jstore.order.domain.order

/** 取消原因分类枚举 */
enum class CancellationCategory {
    BUYER_CANCELLED,
    PAYMENT_TIMEOUT,
    STOCK_INSUFFICIENT,
}

/** 取消原因值对象 不可变，封装取消原因分类和描述 */
data class CancellationReason(
    val category: CancellationCategory,
    val description: String,
) {
    init {
        require(description.isNotBlank()) { "取消原因描述不能为空" }
    }
}
