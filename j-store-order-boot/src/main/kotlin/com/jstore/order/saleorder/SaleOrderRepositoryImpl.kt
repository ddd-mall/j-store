package com.jstore.com.jstore.order.saleorder

import com.fasterxml.jackson.core.type.TypeReference
import com.jstore.com.jstore.order.acl.geo.address.GeoAddressServiceProxy
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPOJpaRepository
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPOJpaRepository
import com.jstore.com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.framework.Page
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.order.saleorder.*
import com.jstore.order.saleorder.properties.UserInfo
import jakarta.transaction.Transactional
import org.springframework.data.domain.AbstractPageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service

@Repository
open class SaleOrderRepositoryImpl(
    private val saleOrderPOJpaRepository: SaleOrderPOJpaRepository,
    private val saleOrderItemPOJpaRepository: SaleOrderItemPOJpaRepository
) : SaleOrderRepository {

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        val saleOrderPOS = saleOrderPOJpaRepository.findSaleOrderPOSByUid(uid)
        if (saleOrderPOS.isEmpty()) {
            return listOf()
        }
        val saleOrderIdList = saleOrderPOS.stream().map { o -> o.saleOrderId }.toList()
        val saleOrderItemPOS = saleOrderItemPOJpaRepository.findSaleOrderItemPOSBySaleOrderIdIsIn(saleOrderIdList)
        return SaleOrderConverter.POs2Entities(saleOrderPOS, saleOrderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        saleOrderPOJpaRepository.findAllByUidOrderByCreateTimeDesc(
            uid,
            object : AbstractPageRequest(currentPage, pageSize) {
                override fun getSort(): Sort {
                    TODO("Not yet implemented")
                }

                override fun next(): Pageable {
                    TODO(" not yet implemented")
                }

                override fun first(): Pageable {
                    TODO("Not yet implemented")
                }

                override fun withPage(pageNumber: Int): Pageable {
                    TODO("Not yet implemented")
                }

                override fun previous(): Pageable {
                    TODO("Not yet implemented")
                }
            })
        TODO(" NOT YET IMPLEMENTED")
    }

    @Transactional(rollbackOn = [Exception::class])
    override fun save(entity: SaleOrder): SaleOrder {
        val holder: SaleOrderPOHolder = SaleOrderConverter.entity2POHolder(entity)

        val savedSaleOrderPO = holder.saleOrderPO!!.let { saleOrderPOJpaRepository.save(it) }
        val savedSaleOrderItemPOs = if (holder.saleOrderItemPOs.isNotEmpty()) {
            saleOrderItemPOJpaRepository.saveAll(holder.saleOrderItemPOs)
        } else {
            listOf()
        }
        return SaleOrderConverter.PO2Entity(savedSaleOrderPO, savedSaleOrderItemPOs)

    }

    override fun findById(id: SaleOrderId): SaleOrder? {
        saleOrderPOJpaRepository.findByIdOrNull(id.value)?.let { saleOrderPO ->
            saleOrderItemPOJpaRepository.findAllBySaleOrderId(id.value).let { saleOrderItemPOs ->
                return SaleOrderConverter.PO2Entity(saleOrderPO, saleOrderItemPOs)
            }
        }
        return null;
    }
}

open class SaleOrderPOHolder {
    var saleOrderPO: SaleOrderPO? = null
    var saleOrderItemPOs: Collection<SaleOrderItemPO> = listOf()
}


@Service
object SaleOrderConverter {


    fun POHolder2Entity(saleOrderPOHolder: SaleOrderPOHolder): SaleOrder? {
        if (saleOrderPOHolder.saleOrderPO == null) {
            return null
        }
        return PO2Entity(saleOrderPOHolder.saleOrderPO!!, saleOrderPOHolder.saleOrderItemPOs)
    }

    fun PO2Entity(saleOrderPO: SaleOrderPO, saleOrderItemPOList: Collection<SaleOrderItemPO>): SaleOrder {
        val id = SaleOrderId(saleOrderPO.saleOrderId)
        val buyerInfo = UserInfo(saleOrderPO.uid, PhoneNumber(saleOrderPO.phoneNumber), saleOrderPO.userName)
        val items: List<OrderItem> = saleOrderItemPOList.map { orderItemPO2Entity(it) }.toList()
        val addressInfo: GeoAddressInfo = GeoAddressServiceProxy.getByDistrictCode(saleOrderPO.districtCode)
            .apply { detailAddress = saleOrderPO.detailAddress }


        val freightBillIds = JsonUtils.deserialize(saleOrderPO.freightBillId, object : TypeReference<List<String>>() {})

        return SaleOrder(
            id,
            buyerInfo,
            items,
            addressInfo,
            null,
            OrderPositiveStatus.valueOf(saleOrderPO.positiveStatus),
            OrderReverseStatus.valueOf(saleOrderPO.reverseStatus),
            Price(saleOrderPO.amount),
            Price(saleOrderPO.actualPay),
            saleOrderPO.createTime,
            saleOrderPO.updateTime
        )
    }

    fun POs2Entities(
        saleOrderPOs: MutableCollection<SaleOrderPO>,
        saleOrderItemPOs: MutableCollection<SaleOrderItemPO>
    ): List<SaleOrder> {
        val itemMap: Map<Long, List<SaleOrderItemPO>> = saleOrderItemPOs.groupBy { item -> item.saleOrderId!! }
        return saleOrderPOs.map { saleOrderPO ->
            PO2Entity(saleOrderPO, itemMap[saleOrderPO.id!!] ?: listOf())
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


    fun entity2POHolder(saleOrder: SaleOrder): SaleOrderPOHolder {
        return SaleOrderPOHolder().apply {
            saleOrderPO = SaleOrderPO().apply {
                saleOrder.getId()?.value?.also { saleOrderId = it }
                saleOrder.buyerInfo.uid.also { uid = it }
                saleOrder.buyerInfo.phoneNumber?.value?.also { phoneNumber = it }
                saleOrder.buyerInfo.userName?.also { userName = it }
                saleOrder.deliveryAddressInfo.districtCode.also { districtCode = it }
                saleOrder.deliveryAddressInfo.detailAddress?.also { detailAddress = it }
                saleOrder.freightBills?.map { it.id }?.toList()
                    .let { JsonUtils.toJsonString(it ?: listOf<String>()) }
                    .also { freightBillId = it }
                saleOrder.positiveStatus.name.also { positiveStatus = it }
                saleOrder.reverseStatus.name.also { reverseStatus = it }
                saleOrder.amount.getBasicValue().also { amount = it }
                saleOrder.actualPay.getBasicValue().also { actualPay = it }
            }

            saleOrderItemPOs = saleOrder.orderItems?.map {
                SaleOrderItemPO().apply {
                    it.id?.also { saleOrderId = it.value }
                    it.count.also { count = it }
                    it.skuId.also { skuId = it.toString() }
                    it.spuId.also { spuId = it.toString() }
                    it.skuVersion.also { skuVersion = it }
                    it.unitPrice.also { unitPrice = it.getBasicValue() }
                    it.totalPrice.also { totalPrice = it.getBasicValue() }
                }
            } ?: listOf()
        }
    }
}