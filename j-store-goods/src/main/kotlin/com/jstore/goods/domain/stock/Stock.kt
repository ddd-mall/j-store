package com.jstore.goods.domain.stock


import com.jstore.common.framework.Entity
import com.jstore.goods.domain.sku.SkuId
import java.math.BigDecimal

interface Stock : Entity<SkuId> {
    fun preDeduct(amount: BigDecimal): StockOperationResponse
    fun add(amount: BigDecimal): StockOperationResponse

    fun confirm(opId: String): StockOperationResponse
    fun rollback(opId: String): StockOperationResponse

}

class StockOperationResponse(
    val opId: String,
    val success: Boolean,
    val message: String,
)