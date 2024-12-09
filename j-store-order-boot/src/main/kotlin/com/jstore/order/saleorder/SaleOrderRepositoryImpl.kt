package com.jstore.com.jstore.order.saleorder

import com.jstore.com.jstore.order.acl.geo.address.GeoAddressService
import com.jstore.com.jstore.order.acl.geo.address.GeoAddressServiceIml
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPOJpaRepository
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPOJpaRepository
import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.framework.Page
import com.jstore.common.properties.PhoneNumber
import com.jstore.order.saleorder.*
import com.jstore.order.saleorder.properties.Price
import com.jstore.order.saleorder.properties.UserInfo
import org.springframework.stereotype.Repository

@Repository
open class SaleOrderRepositoryImpl(
    private val saleOrderPOJpaRepository: SaleOrderPOJpaRepository,
    private val saleOrderItemPOJpaRepository: SaleOrderItemPOJpaRepository,
    private val geoAddressService: GeoAddressService
) : SaleOrderRepository {

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        val saleOrderPOS = saleOrderPOJpaRepository.findSaleOrderPOSByUid(uid)
        if (saleOrderPOS.isEmpty()) {
            return listOf()
        }
        val saleOrderIdList = saleOrderPOS.stream().map { o -> o.saleOrderId!! }.toList()
        val saleOrderItemPOS = saleOrderItemPOJpaRepository.findSaleOrderItemPOSBySaleOrderIdIsIn(saleOrderIdList)
        return POConvertor.pos2Entities(saleOrderPOS, saleOrderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        TODO("Not yet implemented")
    }

    override fun save(entity: SaleOrder): SaleOrder {
        val holder: SaleOrderPOHolder = SaleOrderPOHolder(entity)

        TODO("Not yet implemented")
    }

    override fun findById(id: SaleOrderId): SaleOrder? {
        TODO("Not yet implemented")
    }
}

open class SaleOrderPOHolder {
    constructor()
    constructor(saleOrder: SaleOrder) {
        this.saleOrderPO = SaleOrderPO().apply {
            saleOrderId = saleOrder.getId()?.value
            uid = saleOrder.buyerInfo.uid
            phoneNumber = saleOrder.buyerInfo.phoneNumber?.value
            userName = saleOrder.buyerInfo.userName

        }

        TODO()
    }
    var saleOrderPO: SaleOrderPO? = null
    var saleOrderItemPOs: MutableCollection<SaleOrderItemPO>? = null
}

object POConvertor {
    fun po2Entity(saleOrderPO: SaleOrderPO, saleOrderItemPOList: Collection<SaleOrderItemPO>?): SaleOrder {
        val buyerInfo =
            UserInfo(saleOrderPO.uid!!, saleOrderPO.phoneNumber?.let { PhoneNumber(it) }, saleOrderPO.userName)
        val id = SaleOrderId(saleOrderPO.saleOrderId!!)
        val items: List<OrderItem>? = saleOrderItemPOList?.map { orderItemPO2Entity(it) }?.toList()
        val addressInfo: GeoAddressInfo = GeoAddressServiceIml.getByDistrictCode(saleOrderPO.districtCode!!)
            .apply { detailAddress = saleOrderPO.detailAddress }
        return SaleOrder(
            id,
            buyerInfo,
            items,
            addressInfo,
            null,
            OrderPositiveStatus.valueOf(saleOrderPO.positiveStatus!!),
            OrderReverseStatus.valueOf(saleOrderPO.reverseStatus!!),
            Price(saleOrderPO.amount!!),
            Price(saleOrderPO.actualPay!!),
            saleOrderPO.createTime,
            saleOrderPO.updateTime
        )
    }

    fun pos2Entities(
        saleOrderPOs: MutableCollection<SaleOrderPO>,
        saleOrderItemPOs: MutableCollection<SaleOrderItemPO>
    ): List<SaleOrder> {
        val itemMap: Map<Long, List<SaleOrderItemPO>> = saleOrderItemPOs.groupBy { item -> item.saleOrderId!! }
        return saleOrderPOs.map { saleOrderPO ->
            po2Entity(saleOrderPO, itemMap[saleOrderPO.id!!])
        }
    }

    private fun orderItemPO2Entity(itemPO: SaleOrderItemPO): OrderItem {
        return OrderItem(
            itemPO.saleOrderItemId?.let { OrderItemId(it) },
            itemPO.spuId!!.toLong(),
            itemPO.skuId!!.toLong(),
            itemPO.skuVersion!!,
            itemPO.count!!,
            Price(itemPO.unitPrice!!),
            Price(itemPO.totalPrice!!)
        )
    }

    fun poHolder2Entity(saleOrderPOHolder: SaleOrderPOHolder): SaleOrder? {
        if (saleOrderPOHolder.saleOrderPO == null) {
            return null
        }
        return po2Entity(saleOrderPOHolder.saleOrderPO!!, saleOrderPOHolder.saleOrderItemPOs)
    }

    fun entity2POHolder(saleOrder: SaleOrder): SaleOrderPOHolder {
        SaleOrderPO().apply {
            saleOrderId = saleOrder.getId()?.value
            uid = saleOrder.buyerInfo.uid
            phoneNumber = saleOrder.buyerInfo.phoneNumber?.value
            userName = saleOrder.buyerInfo.userName

        }
        SaleOrderPOHolder()
        TODO()
    }


}