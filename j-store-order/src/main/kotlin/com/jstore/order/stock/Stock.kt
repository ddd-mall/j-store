package com.jstore.order.stock

import com.jstore.com.jstore.framework.Entity
import com.jstore.common.properties.Id
import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.SaleOrderId
import java.util.concurrent.CompletableFuture

interface Stock : Entity<StockId> {
    val id: StockId?
    val orderId: SaleOrderId
    val goodsId: GoodsId
    fun preDeduct(): CompletableFuture<StockOperationResponse>
    fun deduct(): CompletableFuture<StockOperationResponse>
    fun rollback(): CompletableFuture<StockOperationResponse>
}
data class StockId(override val value: Long): Id<Long>(value)

class StockImpl(
    override val id: StockId?,
    override val orderId: SaleOrderId,
    override val goodsId: GoodsId
): Stock {
    override fun preDeduct(): CompletableFuture<StockOperationResponse> {
        TODO("Not yet implemented")
    }

    override fun deduct(): CompletableFuture<StockOperationResponse> {
        TODO("Not yet implemented")
    }

    override fun rollback(): CompletableFuture<StockOperationResponse> {
        TODO("Not yet implemented")
    }

    override fun getId(): StockId? {
        return id
    }
}

data class StockOperationResponse(
    val opId: String,
    val success: Boolean,
    val message: String,
)