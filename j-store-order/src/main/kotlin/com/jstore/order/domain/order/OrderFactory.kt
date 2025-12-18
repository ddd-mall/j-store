package com.jstore.order.domain.order

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.domain.order.command.PurchaseItem
import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.acl.GeoAddressService
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.item.NormalItem
import com.jstore.order.domain.order.item.OrderItemId
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class OrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val snowFlakSequence: SnowFlakSequence,
) {

    fun create(createCmd: OrderCreateCMD): OrderImpl {

        val orderItemImpls: List<NormalItem> = createOrderItems(createCmd)

        return OrderImpl(
            id = OrderId(snowFlakSequence.nextId()),
            buyerInfo = createCmd.buyerUserInfo,
            orderItemImpls = orderItemImpls,
            shippingAddressInfo = queryAddressInfoDetail(createCmd).also { it.detailAddress = createCmd.detailAddress },
            status = OrderStatus.CREATED,
            amount = sumOf(orderItemImpls.map { orderItem -> orderItem.totalPrice }),
            actualPay = Price.Companion.Commonly.of(0),
            createTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now()
        )
    }

    private fun queryAddressInfoDetail(createCmd: OrderCreateCMD): GeoAddressInfo {
        return geoAddressService.getByDistrictCode(createCmd.districtCode)
    }


    private fun createOrderItems(createCmd: OrderCreateCMD): List<NormalItem> {
        val goodsInfoQueryHelper: GoodsInfoQueryHelper = queryGoodsInfo(createCmd)

        return createCmd.purchaseItemList.map { purchaseItem: PurchaseItem ->
            val goodsInfo = goodsInfoQueryHelper.find(purchaseItem.spuId, purchaseItem.skuId)
            createOrderItem(purchaseItem, goodsInfo)
        }
    }

    private fun createOrderItem(purchaseItem: PurchaseItem, goodsInfo: GoodsInfo): NormalItem {
        return NormalItem(
            id = OrderItemId(snowFlakSequence.nextId()),
            quantity = purchaseItem.quantity,
            unitPrice = goodsInfo.price,
            totalPrice = goodsInfo.price.multiple(purchaseItem.quantity),
            itemStatus = OrderItemStatus.WAIT_SHIPPING
        )
    }

    private fun queryGoodsInfo(createCmd: OrderCreateCMD): GoodsInfoQueryHelper {
        val goodsIdList: List<GoodsId> = createCmd.purchaseItemList.map { it.mapToGoodsId() }.toList()
        return GoodsInfoQueryHelper(goodsService.queryGoods(goodsIdList))
    }

    class GoodsInfoQueryHelper(private val goodsQueryResult: List<GoodsInfo>) {
        fun find(spuId: Long, skuId: Long): GoodsInfo {
            return goodsQueryResult.find { it.id.spuId == spuId && it.id.skuId == skuId }
                ?: throw OrderErrors.CORRESPONDING_GOODS_NOT_FOUND.msg("spuId: $spuId and skuId: $skuId corresponding goods not found")
        }
    }

}




