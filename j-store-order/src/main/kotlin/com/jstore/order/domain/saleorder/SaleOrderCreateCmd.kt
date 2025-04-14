package com.jstore.order.domain.saleorder

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.order.acl.GoodsId
import com.jstore.order.domain.saleorder.properties.GeoAddressInfo
import com.jstore.order.domain.saleorder.properties.UserInfo
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * 普通订单创建命令，
 */
class SaleOrderCreateCmd(val token: String) {

    lateinit var buyerUserInfo: UserInfo
    lateinit var purchaseItemList: List<PurchaseItem>
    var districtCode: String = ""

    val addressInfo: GeoAddressInfo? = null
    var detailAddress: String = ""

    class PurchaseItem {
        var spuId: Long? = null
        var skuId: Long? = null
        var quantity: BigDecimal = BigDecimal.ZERO

        fun mapToGoodsId(): GoodsId {
            return GoodsId(
                spuId ?: throw IllegalArgumentException("spuId can not be null"),
                skuId ?: throw IllegalArgumentException("skuId can not be null")
            )
        }

        override fun toString(): String {
            return "PurchaseItem(spuId=$spuId, skuId=$skuId, count=$quantity)"
        }
    }
}

@Service
class SaleOrderCreateCMDHandler(
    private val saleOrderRepository: SaleOrderRepository,
    private val saleOrderFactory: SaleOrderFactory,
    private val domainEventPublisher: DomainEventPublisher,
) {
    fun create(cmd: SaleOrderCreateCmd): SaleOrder {
        val saleOrder = this.saleOrderFactory.create(cmd)
        val saved = saleOrderRepository.save(saleOrder)
        domainEventPublisher.publishEvent(SaleOrderCreatedEvent(order = saved, this))
        return saved
    }
}