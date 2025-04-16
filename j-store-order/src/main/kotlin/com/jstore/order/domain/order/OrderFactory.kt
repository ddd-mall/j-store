package com.jstore.order.domain.order

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.domain.order.command.PurchaseItem
import com.jstore.order.domain.order.command.OrderCreateCmd
import com.jstore.order.service.acl.GeoAddressService
import com.jstore.order.service.acl.GoodsId
import com.jstore.order.service.acl.GoodsInfo
import com.jstore.order.service.acl.GoodsService
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class OrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val snowFlakSequence: SnowFlakSequence,
) {

    fun create(createCmd: OrderCreateCmd): Order {

        val orderItems: List<OrderItem> = createOrderItems(createCmd)

        return Order(
            id = OrderId(snowFlakSequence.nextId()),
            buyerInfo = createCmd.buyerUserInfo,
            orderItems = orderItems,
            deliveryAddressInfo = queryAddressInfoDetail(createCmd).also { it.detailAddress = createCmd.detailAddress },
            status = OrderStatus.NONE,
            amount = sumOf(orderItems.map { orderItem -> orderItem.totalPrice }),
            actualPay = Price.Companion.Commonly.of(0),
            createTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now()
        )
    }

    private fun queryAddressInfoDetail(createCmd: OrderCreateCmd): GeoAddressInfo {
        return geoAddressService.getByDistrictCode(createCmd.districtCode)
    }


    private fun createOrderItems(createCmd: OrderCreateCmd): List<OrderItem> {
        val goodsInfoQueryHelper: GoodsInfoQueryHelper = queryGoodsInfo(createCmd)

        return createCmd.purchaseItemList.map { purchaseItem: PurchaseItem ->
            val goodsInfo = goodsInfoQueryHelper.find(purchaseItem.spuId, purchaseItem.skuId)
            createOrderItem(purchaseItem, goodsInfo)
        }
    }

    private fun createOrderItem(purchaseItem: PurchaseItem, goodsInfo: GoodsInfo): OrderItem {
        return OrderItem(
            id = OrderItemId(snowFlakSequence.nextId()),
            goodsId = goodsInfo.id,
            goodsVersion = goodsInfo.version,
            quantity = purchaseItem.quantity,
            unitPrice = goodsInfo.price,
            totalPrice = goodsInfo.price.multiple(purchaseItem.quantity)
        )
    }

    private fun queryGoodsInfo(createCmd: OrderCreateCmd): GoodsInfoQueryHelper {
        val goodsIdList: List<GoodsId> = createCmd.purchaseItemList.map { it.mapToGoodsId() }.toList()
        return GoodsInfoQueryHelper(goodsService.queryGoods(goodsIdList))
    }

    class GoodsInfoQueryHelper(private val goodsQueryResult: List<GoodsInfo>) {
        fun find(spuId: Long, skuId: Long): GoodsInfo {
            return goodsQueryResult.find { it.id.spuId == spuId && it.id.skuId == skuId }
                ?: throw OrderErrors.CorrespondingGoodsNotFound.msg("spuId: $spuId and skuId: $skuId corresponding goods not found")
        }
    }

}




