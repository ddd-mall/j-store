package com.jstore.goods.stock

import com.jstore.com.jstore.framework.Entity
import com.jstore.goods.spu.SkuId
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