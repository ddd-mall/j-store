package com.jstore.order.domain.saleorder

import com.jstore.common.errors.CommonErrors
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.acl.*
import com.jstore.order.domain.saleorder.properties.GeoAddressInfo
import com.jstore.order.domain.saleorder.properties.UserInfo
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDUserInfoValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderCreateCMDValidChain
import com.jstore.order.domain.saleorder.validator.SaleOrderRiskValidator
import com.jstore.order.domain.saleorder.validator.SaleOrderValidChain
import org.springframework.stereotype.Service

@Service
open class NormalSaleOrderFactory(
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

    fun create(createParam: NormalSaleOrderCreateCmd): SaleOrder {
        createParamValidChain.accept(createParam)
        val saleOrder = convertParamToNormalSaleOrder(createParam)
        saleOrderValidChain.accept(saleOrder)
        return saleOrder
    }

    private fun convertParamToNormalSaleOrder(createParam: NormalSaleOrderCreateCmd): SaleOrder {
        val userInfo: UserInfo = createParam.buyerUserInfo
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

    private fun getOrderItemsFromCreateParam(createParam: NormalSaleOrderCreateCmd): List<OrderItem> {
        val purchaseItemList = createParam.purchaseItemList
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        return purchaseItemList.map { purchaseItem: NormalSaleOrderCreateCmd.PurchaseItem ->
            val goodsInfo =
                goodsQueryResult.find { it.id.spuId == purchaseItem.spuId && it.id.skuId == purchaseItem.skuId }
                    ?: throw CommonErrors.RESOURCE_NOT_FOUND.to("the goods corresponding to purchase item $purchaseItem not found")

            val totalPrice: Price = goodsInfo.price.multiple(purchaseItem.count)
            val item = OrderItem(
                null,

                goodsInfo.id.spuId,
                goodsInfo.id.skuId,
                goodsInfo.version,
                purchaseItem.count,
                goodsInfo.price,
                totalPrice
            )
            item
        }
    }
}




