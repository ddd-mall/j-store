package com.jstore.com.jstore.order.saleorder.service

import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.com.jstore.order.saleorder.validator.SaleOrderCreateParamValidChain
import com.jstore.com.jstore.order.saleorder.validator.SaleOrderValidChain
import com.jstore.order.acl.goods.GoodsId
import com.jstore.order.acl.goods.GoodsInfo
import com.jstore.order.acl.goods.GoodsService
import com.jstore.order.saleorder.*
import com.jstore.order.saleorder.properties.Price
import com.jstore.order.saleorder.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.saleorder.properties.UserInfo

class OrderService(
    private val saleOrderRepository: SaleOrderRepository,
    private val goodsService: GoodsService,
    private val createParamValidChain: SaleOrderCreateParamValidChain,
    private val saleOrderValidChain: SaleOrderValidChain
    ) {


    fun createSaleOrder(createParam: SaleOrderCreateParam): SaleOrder {
        createParamValidChain.accept(createParam)
        val saleOrder = convertParamToSaleOrder(createParam)
        saleOrderValidChain.accept(saleOrder)
        return saleOrderRepository.save(saleOrder)
    }

    private fun convertParamToSaleOrder(createParam: SaleOrderCreateParam): SaleOrder {
        val userInfo: UserInfo = getUserInfoFromCreateParam(createParam)
        val orderItems: List<OrderItem> = getOrderItemsFromCreateParam(createParam)
        val deliveryAddressInfo: GeoAddressInfo = getDeliveryAddressInfoFromCreateParam(createParam)

        val amount: Price = sumOf(orderItems.map { orderItem -> orderItem.totalPrice })
        return SaleOrder(
            null,
            userInfo,
            orderItems,
            deliveryAddressInfo,
            null,
            OrderPositiveStatus.CREATING,
            OrderReverseStatus.NONE,
            amount,
            Price.Companion.Commonly.of(0)
        )
    }

    private fun getOrderItemsFromCreateParam(createParam: SaleOrderCreateParam): List<OrderItem> {
        val purchaseItemList = createParam.purchaseItemList!!
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        return purchaseItemList.map { purchaseItem: SaleOrderCreateParam.PurchaseItem ->
            val goodsInfo = goodsQueryResult.find { it.spuId == purchaseItem.spuId && it.skuId == purchaseItem.skuId }
                ?: throw IllegalArgumentException("Goods $purchaseItem not found")

            val totalPrice: Price = goodsInfo.price.multiple(purchaseItem.count!!)
            val item = OrderItem(
                null,

                goodsInfo.spuId,
                goodsInfo.skuId,
                goodsInfo.version,
                purchaseItem.count!!,
                goodsInfo.price,
                totalPrice
            )
            item
        }
    }

    private fun getDeliveryAddressInfoFromCreateParam(createParam: SaleOrderCreateParam): GeoAddressInfo {
        return createParam.deliveryAddressInfo!!
    }

    private fun getUserInfoFromCreateParam(createParam: SaleOrderCreateParam): UserInfo {
        return createParam.buyerUserInfo!!
    }


}


class SaleOrderCreateParam {
    var buyerUserInfo: UserInfo? = null
    var purchaseItemList: List<PurchaseItem>? = null
    var deliveryAddressInfo: GeoAddressInfo? = null

    class PurchaseItem {
        var spuId: Long? = null
        var skuId: Long? = null
        var count: Int? = 0

        fun mapToGoodsId(): GoodsId {
            return GoodsId(
                spuId ?: throw IllegalArgumentException("spuId can not be null"),
                skuId ?: throw IllegalArgumentException("skuId can not be null")
            )
        }
    }

}

