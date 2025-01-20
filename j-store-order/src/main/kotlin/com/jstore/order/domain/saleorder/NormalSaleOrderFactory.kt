package com.jstore.order.domain.saleorder

import com.jstore.common.framework.DomainEventId
import com.jstore.common.persistent.SnowFlakSequence
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
import java.time.LocalDateTime

@Service
open class NormalSaleOrderFactory(
    private val goodsService: GoodsService,
    private val geoAddressService: GeoAddressService,
    private val saleOrderEventPublisher: SaleOrderEventPublisher,
    private val snowFlakSequence: SnowFlakSequence,
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
        saleOrderEventPublisher.publish(
            SaleOrderCreatedEvent(
                id = DomainEventId(snowFlakSequence.nextId()),
                order = saleOrder,
            )
        )
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
            id = SaleOrderId(snowFlakSequence.nextId()),
            buyerInfo = userInfo,
            orderItems = orderItems,
            deliveryAddressInfo = deliveryAddressInfo,
            freightBills = null,
            positiveStatus = OrderPositiveStatus.WAIT_PAY,
            reverseStatus = OrderReverseStatus.NONE,
            amount = amount,
            actualPay = Price.Companion.Commonly.of(0),
            createTime = LocalDateTime.now(),
            updateTime = LocalDateTime.now()
        )
    }

    private fun getOrderItemsFromCreateParam(createParam: NormalSaleOrderCreateCmd): List<OrderItem> {
        val purchaseItemList = createParam.purchaseItemList
        val goodsIdList: List<GoodsId> = purchaseItemList.map { it.mapToGoodsId() }
        val goodsQueryResult: List<GoodsInfo> = goodsService.queryGoods(goodsIdList)

        return purchaseItemList.map { purchaseItem: NormalSaleOrderCreateCmd.PurchaseItem ->
            val goodsInfo =
                goodsQueryResult.find { it.id.spuId == purchaseItem.spuId && it.id.skuId == purchaseItem.skuId }
                    ?: throw SaleOrderErrors.CorrespondingGoodsNotFound.msg("purchase item $purchaseItem corresponding goods not found")

            val totalPrice: Price = goodsInfo.price.multiple(purchaseItem.quantity)
            val item = OrderItem(
                id = OrderItemId(snowFlakSequence.nextId()),
                spuId = goodsInfo.id.spuId,
                skuId = goodsInfo.id.skuId,
                skuVersion = goodsInfo.version,
                count = purchaseItem.quantity,
                unitPrice = goodsInfo.price,
                totalPrice = totalPrice
            )
            item
        }
    }
}




