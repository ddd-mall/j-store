package com.jstore.order.saleorder.service

import com.jstore.common.errors.CommonErrors
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.order.saleorder.validator.SaleOrderCreateParamValidChain
import com.jstore.order.saleorder.validator.SaleOrderValidChain
import com.jstore.order.acl.goods.GoodsId
import com.jstore.order.acl.goods.GoodsInfo
import com.jstore.order.acl.goods.GoodsService
import com.jstore.order.saleorder.*
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.acl.geo.address.GeoAddressService
import com.jstore.order.saleorder.properties.UserInfo
import org.springframework.stereotype.Service

@Service
open class OrderService(
    private val saleOrderRepository: SaleOrderRepository,
    private val goodsService: GoodsService,
    private val createParamValidChain: SaleOrderCreateParamValidChain,
    private val saleOrderValidChain: SaleOrderValidChain,
    private val geoAddressService: GeoAddressService
) {


    fun createSaleOrder(createParam: SaleOrderCreateParam): SaleOrder {
        createParamValidChain.accept(createParam)
        val saleOrder = convertParamToSaleOrder(createParam)
        saleOrderValidChain.accept(saleOrder)
        return saleOrderRepository.save(saleOrder)
    }

    private fun convertParamToSaleOrder(createParam: SaleOrderCreateParam): SaleOrder {
        val userInfo: UserInfo = createParam.buyerUserInfo!!
        val orderItems: List<OrderItem> = getOrderItemsFromCreateParam(createParam)
        val deliveryAddressInfo: GeoAddressInfo = geoAddressService.getByDistrictCode(createParam.districtCode).apply { detailAddress = createParam.detailAddress }

        val amount: Price = sumOf(orderItems.map { orderItem -> orderItem.totalPrice })
        return SaleOrder(
            null,
            userInfo,
            orderItems,
            deliveryAddressInfo,
            null,
            OrderPositiveStatus.WAIT_PAY,
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
            val goodsInfo = goodsQueryResult.find { it.id.spuId == purchaseItem.spuId && it.id.skuId == purchaseItem.skuId }
                ?: throw CommonErrors.RESOURCE_NOT_FOUND.withMsg("the goods corresponding to purchase item $purchaseItem not found")

            val totalPrice: Price = goodsInfo.price.multiple(purchaseItem.count!!)
            val item = OrderItem(
                null,

                goodsInfo.id.spuId,
                goodsInfo.id.skuId,
                goodsInfo.version,
                purchaseItem.count!!,
                goodsInfo.price,
                totalPrice
            )
            item
        }
    }
}


class SaleOrderCreateParam {
    var buyerUserInfo: UserInfo? = null
    var purchaseItemList: List<PurchaseItem>? = null
    var districtCode: String = ""
    var detailAddress: String = ""

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

        override fun toString(): String {
            return "PurchaseItem(spuId=$spuId, skuId=$skuId, count=$count)"
        }
    }
}

