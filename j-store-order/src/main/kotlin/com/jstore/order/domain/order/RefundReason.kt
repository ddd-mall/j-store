package com.jstore.order.domain.order

/**
 * 退款原因分类枚举
 */
enum class RefundCategory {
    NO_LONGER_NEEDED,
    NOT_AS_DESCRIBED,
    QUALITY_ISSUE,
    OTHER
}

/**
 * 退款原因值对象
 * 不可变，封装退款原因分类和描述
 */
data class RefundReason(
    val category: RefundCategory,
    val description: String
) {
    init {
        require(description.isNotBlank()) { "退款原因描述不能为空" }
    }
}
