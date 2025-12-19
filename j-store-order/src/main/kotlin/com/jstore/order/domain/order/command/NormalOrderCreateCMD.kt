package com.jstore.order.domain.order.command

import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.order.NormalOrderFactory
import com.jstore.order.domain.order.NormalOrderImpl
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.UserInfo
import java.math.BigDecimal

/**
 * 普通订单创建命令，
 */
class NormalOrderCreateCMD(
    val token: String,
    val buyerUserInfo: UserInfo,
    val purchaseItemList: List<PurchaseItem>,
    val districtCode: String = "",
    val detailAddress: String = "",
)

class PurchaseItem(
    val spuId: Long = 0L,
    val skuId: Long = 0L,
    val quantity: BigDecimal = BigDecimal.ZERO,
) {

    fun mapToGoodsId(): GoodsId {
        return GoodsId(spuId, skuId)
    }

    override fun toString(): String {
        return "PurchaseItem(spuId=$spuId, skuId=$skuId, count=$quantity)"
    }
}


class OrderCreateHandler(
    private val orderRepository: OrderRepository,
    private val normalOrderFactory: NormalOrderFactory,
) {

    fun create(cmd: NormalOrderCreateCMD): NormalOrderImpl {
        val order = this.normalOrderFactory.create(cmd)
        return orderRepository.save(order)
    }
}