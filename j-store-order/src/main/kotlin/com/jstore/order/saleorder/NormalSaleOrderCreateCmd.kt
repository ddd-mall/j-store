package com.jstore.order.saleorder

import com.jstore.order.acl.GoodsId
import com.jstore.order.saleorder.properties.UserInfo
import org.springframework.lang.Nullable
import org.springframework.stereotype.Service

/**
 * 普通订单创建命令，
 */
class NormalSaleOrderCreateCmd(val token: String) {

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

@Service
class NormalSaleOrderCreateCMDHandler(
    private val saleOrderRepository: SaleOrderRepository,
    private val normalSaleOrderFactory: NormalSaleOrderFactory,
    @Nullable
    private val saleOrderEventPublisher: SaleOrderEventPublisher?
) {
    fun create(cmd: NormalSaleOrderCreateCmd): SaleOrder {
        val saleOrder = this.normalSaleOrderFactory.create(cmd)
        val saved = saleOrderRepository.save(saleOrder)
        saleOrderEventPublisher?.publish(NormalSaleOrderCreatedEvent(saved.getId()!!, saved.createTime!!))
        return saved
    }
}