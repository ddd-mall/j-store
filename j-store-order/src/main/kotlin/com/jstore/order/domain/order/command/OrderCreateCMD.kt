package com.jstore.order.domain.order.command

import com.jstore.order.domain.order.UserInfo
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 普通订单创建命令，
 */
class OrderCreateCMD(
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


@Service
class OrderCreateHandler(
    private val orderRepository: OrderRepository,
    private val orderFactory: OrderFactory,
) {

    @Transactional(
        rollbackFor = [Exception::class],
        propagation = Propagation.REQUIRED
    )
    fun create(cmd: OrderCreateCMD): Order {
        val order = this.orderFactory.create(cmd)
        order.initial()
        return orderRepository.save(order)
    }
}