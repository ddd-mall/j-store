package com.jstore.order.domain.order

import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.properties.Price
import com.jstore.common.properties.Price.Companion.Commonly.sumOf
import com.jstore.order.domain.order.command.PurchaseItem
import com.jstore.order.domain.order.command.NormalOrderCreateCMD
import com.jstore.order.acl.GeoAddressService
import com.jstore.order.acl.GoodsId
import com.jstore.order.acl.GoodsInfo
import com.jstore.order.acl.GoodsService
import com.jstore.order.domain.order.event.OrderCreatedEvent
import com.jstore.order.domain.order.item.NormalItem
import com.jstore.order.domain.order.item.OrderItemId
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class NormalOrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val snowFlakSequence: SnowFlakSequence,
) {

    fun create(createCmd: NormalOrderCreateCMD): NormalOrderImpl {

        val orderItemImpls: List<NormalItem> = buildOrderItems(createCmd)

        val normalOrderImpl = NormalOrderImpl(
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
        normalOrderImpl.publishEvent(OrderCreatedEvent(this, normalOrderImpl))
        return normalOrderImpl
    }

    private fun queryAddressInfoDetail(createCmd: NormalOrderCreateCMD): GeoAddressInfo {
        return geoAddressService.getByDistrictCode(createCmd.districtCode)
    }


    private fun buildOrderItems(createCmd: NormalOrderCreateCMD): List<NormalItem> {
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
        )
    }

    private fun queryGoodsInfo(createCmd: NormalOrderCreateCMD): GoodsInfoQueryHelper {
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




