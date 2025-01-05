package com.jstore.order.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.acl.GeoAddressService
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.order.saleorder.properties.UserInfo
import com.jstore.order.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.saleorder.validator.SaleOrderCreateCMDValidChain
import com.jstore.order.saleorder.validator.SaleOrderRiskValidator
import com.jstore.order.saleorder.validator.SaleOrderValidChain
import org.springframework.stereotype.Service

@Service
open class SaleOrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    saleOrderCreateCMDValidator: SaleOrderCreateCMDUserInfoValidator,
    saleOrderRiskValidator: SaleOrderRiskValidator

) {
    companion object {
        private val createParamValidChain: SaleOrderCreateCMDValidChain = SaleOrderCreateCMDValidChain()
        private val saleOrderValidChain: SaleOrderValidChain = SaleOrderValidChain()
    }
    init {
        createParamValidChain.appendAll(saleOrderCreateCMDValidator)
        saleOrderValidChain.appendAll(saleOrderRiskValidator)
    }

    fun create(createParam: SaleOrderCreateCMD): SaleOrder {
        createParamValidChain.accept(createParam)
        val saleOrder = convertParamToSaleOrder(createParam)
        saleOrderValidChain.accept(saleOrder)

        return saleOrder
    }

    private fun convertParamToSaleOrder(createParam: SaleOrderCreateCMD): SaleOrder {
        val userInfo: UserInfo = createParam.buyerUserInfo!!
        val orderItems: List<OrderItem> = getOrderItemsFromCreateParam(createParam)
        val deliveryAddressInfo: GeoAddressInfo = geoAddressService
            .getByDistrictCode(createParam.districtCode)
            .apply { detailAddress = createParam.detailAddress }

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

    private fun getOrderItemsFromCreateParam(createParam: SaleOrderCreateCMD): List<OrderItem> {
        val purchaseItemList = createParam.purchaseItemList!!
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        return purchaseItemList.map { purchaseItem: SaleOrderCreateCMD.PurchaseItem ->
            val goodsInfo =
                goodsQueryResult.find { it.id.spuId == purchaseItem.spuId && it.id.skuId == purchaseItem.skuId }
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




