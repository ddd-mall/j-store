package com.jstore.order.domain.saleorder

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.acl.GeoAddressService
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.saleorder.properties.GeoAddressInfo
import com.jstore.order.domain.saleorder.properties.UserInfo
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SaleOrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val snowFlakSequence: SnowFlakSequence,
) {




    fun create(createCmd: SaleOrderCreateCmd): SaleOrder {
        val userInfo: UserInfo = createCmd.buyerUserInfo
        val orderItems: List<OrderItem> = getOrderItemsFromCreateParam(createCmd)
        val deliveryAddressInfo: GeoAddressInfo = geoAddressService
            .getByDistrictCode(createCmd.districtCode)
            .apply { detailAddress = createCmd.detailAddress }

        val amount: Price = sumOf(orderItems.map { orderItem -> orderItem.totalPrice })
        return SaleOrder(
            id = SaleOrderId(snowFlakSequence.nextId()),
            buyerInfo = userInfo,
            orderItems = orderItems,
            deliveryAddressInfo = deliveryAddressInfo,
            positiveStatus = OrderPositiveStatus.WAIT_PAY,
            reverseStatus = OrderReverseStatus.NONE,
            amount = amount,
            actualPay = Price.Companion.Commonly.of(0),
            createTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now()
        )
    }


    private fun getOrderItemsFromCreateParam(createCmd: SaleOrderCreateCmd): List<OrderItem> {
        val purchaseItemList = createCmd.purchaseItemList
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        return purchaseItemList.map { purchaseItem: SaleOrderCreateCmd.PurchaseItem ->
            val goodsInfo =
                goodsQueryResult.find { it.id.spuId == purchaseItem.spuId && it.id.skuId == purchaseItem.skuId }
                    ?: throw SaleOrderErrors.CorrespondingGoodsNotFound.msg("purchase item $purchaseItem corresponding goods not found")

            val totalPrice: Price = goodsInfo.price.multiple(purchaseItem.quantity)
            val item = OrderItem(
                id = OrderItemId(snowFlakSequence.nextId()),
                spuId = goodsInfo.id.spuId,
                skuId = goodsInfo.id.skuId,
                goodsVersion = goodsInfo.version,
                quantity = purchaseItem.quantity,
                unitPrice = goodsInfo.price,
                totalPrice = totalPrice
            )
            item
        }
    }
}




