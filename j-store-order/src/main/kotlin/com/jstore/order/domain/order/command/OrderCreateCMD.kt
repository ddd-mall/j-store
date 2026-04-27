package com.jstore.order.domain.order.command

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.ContractInfo
import com.jstore.order.domain.order.OrderErrors

/**
 * 创建订单命令
 */
data class OrderCreateCMD(
    val buyerUid: Long,
    val buyerPhone: String?,
    val buyerName: String?,
    val consigneeContractInfo: ContractInfo,
    val shippingDistrictCode: String,
    val countryCode: String? = null,
    val shippingDetailAddress: String? = null,
    val items: List<OrderItemCMD>,
) {
    data class OrderItemCMD(
        val spuId: Long,
        val skuId: Long,
        val quantity: Int,
    )

    fun validate(): Result<OrderCreateCMD, BusinessError> {
        if (items.isEmpty()) return Failure(OrderErrors.ITEMS_EMPTY)
        if (buyerUid <= 0) return Failure(OrderErrors.BUYER_INVALID)
        return Success(this)
    }
}
