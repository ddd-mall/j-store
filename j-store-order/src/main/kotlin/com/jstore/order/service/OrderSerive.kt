package com.jstore.order.service

import com.jstore.order.acl.goods.GoodsId
import com.jstore.order.acl.goods.GoodsInfo
import com.jstore.order.acl.goods.GoodsService
import com.jstore.order.saleorder.OrderItem
import com.jstore.order.saleorder.SaleOrder
import com.jstore.order.saleorder.SaleOrderRepository
import com.jstore.order.saleorder.UserInfo
import com.jstore.util.ChainedConsumer

class OrderSerive(
    private val saleOrderRepository: SaleOrderRepository,
    private val goodsService: GoodsService,
    private val createParamValidator: createParamValidator
) {
    init {
        validatorInit(createParamValidator)
    }


    fun createSaleOrder(createParam: SaleOrderCreateParam): SaleOrder {
        // 校验参数
        createParamValidator.accept(createParam)
        val orderItems: List<OrderItem> = getOrderItemsFromCreateParam(createParam)

        return TODO("Provide the return value")
    }

    private fun getOrderItemsFromCreateParam(createParam: SaleOrderCreateParam): List<OrderItem> {
        val purchaseItemList = createParam.purchaseItemList!!
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        purchaseItemList.map { purchaseItem -> {
            val goodsInfo = goodsQueryResult.find { it.spuId == purchaseItem.spuId && it.skuId == purchaseItem.skuId }
                ?: throw IllegalArgumentException("Goods $purchaseItem not found")

        } }

        for (purchaseItem in purchaseItemList) {
            val goodsInfo = (goodsQueryResult.find { it.spuId == purchaseItem.spuId && it.skuId == purchaseItem.skuId }
                ?: throw IllegalArgumentException("Goods $purchaseItem not found"))
        }

        return TODO();
    }

    private fun validatorInit(validator: createParamValidator) {
        TODO()
    }
}

class createParamValidator(consumers: List<ChainedConsumer<SaleOrderCreateParam>>?) :
    ChainedConsumer.ConsumerChain<SaleOrderCreateParam>() {
    init {
        consumers?.forEach { this.append(it) }
    }
}

class SaleOrderCreateParam {
    var buyerUserInfo: UserInfo? = null
    var purchaseItemList: List<PurchaseItem>? = null

}

class PurchaseItem {
    var spuId: Long? = null;
    var skuId: Long? = null;
    var count: Int? = 0;

    fun mapToGoodsId(): GoodsId {
        return GoodsId(
            spuId ?: throw IllegalArgumentException("spuId can not be null"),
            skuId ?: throw IllegalArgumentException("skuId can not be null")
        )
    }
}