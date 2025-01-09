package com.jstore.com.jstore.order.saleorder

import com.fasterxml.jackson.core.type.TypeReference
import com.jstore.com.jstore.order.acl.geo.address.GeoAddressServiceProxy
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderItemPOJpaRepository
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPO
import com.jstore.com.jstore.order.saleorder.persistence.SaleOrderPOJpaRepository
import com.jstore.order.saleorder.properties.GeoAddressInfo
import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.order.saleorder.*
import com.jstore.order.saleorder.properties.UserInfo
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.LocalDateTime


@Repository
class SaleOrderRepositoryImpl(
    private val saleOrderPOJpaRepository: SaleOrderPOJpaRepository,
    private val saleOrderItemPOJpaRepository: SaleOrderItemPOJpaRepository
) : SaleOrderRepository {

    override fun findByBuyerUserId(uid: Long): List<SaleOrder> {
        val saleOrderPOS = saleOrderPOJpaRepository.findSaleOrderPOSByUid(uid)
        if (saleOrderPOS.isEmpty()) {
            return listOf()
        }
        val saleOrderIdList = saleOrderPOS.stream().map { o -> o.saleOrderId }.toList()
        val saleOrderItemPOS = saleOrderItemPOJpaRepository.findAllBySaleOrderIdIsIn(saleOrderIdList)
        return SaleOrderConverter.pos2Entities(saleOrderPOS, saleOrderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<SaleOrder> {
        val saleOrderPOPage = saleOrderPOJpaRepository.findAllByUidOrderByCreateTimeDesc(
            uid,
            PageRequest.of(currentPage, pageSize, Sort.by(listOf(Order.desc("create_time"))))
        )
        val saleOrderPOS = saleOrderPOPage.get().toList()
        val saleOrderItemPOList = saleOrderPOPage.get().map { it.saleOrderId }.toList().let { saleOrderIds ->
            saleOrderItemPOJpaRepository.findAllBySaleOrderIdIsIn(saleOrderIds)
        }

        return SortedPage(currentPage, pageSize, SaleOrderConverter.pos2Entities(saleOrderPOS, saleOrderItemPOList))
    }

    @Transactional(rollbackOn = [Exception::class])
    override fun save(entity: SaleOrder): SaleOrder {
        val holder: SaleOrderPOHolder = SaleOrderConverter.entity2POHolder(entity)

        val savedSaleOrderPO = holder.saleOrderPO.let { saleOrderPOJpaRepository.save(it.apply { updateTime = LocalDateTime.now() }) }
        val savedSaleOrderItemPOs = if (holder.saleOrderItemPOs.isNotEmpty()) {
            saleOrderItemPOJpaRepository.saveAll(holder.saleOrderItemPOs)
        } else {
            listOf()
        }
        return SaleOrderConverter.po2Entity(savedSaleOrderPO, savedSaleOrderItemPOs)

    }

    override fun findById(id: SaleOrderId): SaleOrder? {
        saleOrderPOJpaRepository.findByIdOrNull(id.value)?.let { saleOrderPO ->
            saleOrderItemPOJpaRepository.findAllBySaleOrderId(id.value).let { saleOrderItemPOs ->
                return SaleOrderConverter.po2Entity(saleOrderPO, saleOrderItemPOs)
            }
        }
        return null
    }
}

open class SaleOrderPOHolder {
    lateinit var saleOrderPO: SaleOrderPO
    var saleOrderItemPOs: Collection<SaleOrderItemPO> = listOf()
}


object SaleOrderConverter {
    fun poHolder2Entity(saleOrderPOHolder: SaleOrderPOHolder): SaleOrder {
        return po2Entity(saleOrderPOHolder.saleOrderPO, saleOrderPOHolder.saleOrderItemPOs)
    }

    fun po2Entity(saleOrderPO: SaleOrderPO, saleOrderItemPOList: Collection<SaleOrderItemPO>): SaleOrder {
        val id = SaleOrderId(saleOrderPO.saleOrderId)
        val buyerInfo = UserInfo(saleOrderPO.uid, PhoneNumber(saleOrderPO.phoneNumber), saleOrderPO.userName)
        val items: List<OrderItem> = saleOrderItemPOList.map { orderItemPO2Entity(it) }.toList()
        val addressInfo: GeoAddressInfo = GeoAddressServiceProxy.getByDistrictCode(saleOrderPO.districtCode)
            .apply { detailAddress = saleOrderPO.detailAddress }

        val freightBillIds = JsonUtils.deserialize(saleOrderPO.freightBillId, object : TypeReference<List<String>>() {})
        return NormalSaleOrderImpl(
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

    fun pos2Entities(
        saleOrderPOs: Collection<SaleOrderPO>,
        saleOrderItemPOs: Collection<SaleOrderItemPO>
    ): List<SaleOrder> {
        val itemMap: Map<Long, List<SaleOrderItemPO>> = saleOrderItemPOs.groupBy { item -> item.saleOrderId }
        return saleOrderPOs.map { saleOrderPO ->
            po2Entity(saleOrderPO, itemMap[saleOrderPO.saleOrderId] ?: listOf())
        }
    }

    private fun orderItemPO2Entity(itemPO: SaleOrderItemPO): OrderItem {
        return OrderItem(
            itemPO.saleOrderItemId.let { OrderItemId(it) },
            itemPO.spuId.toLong(),
            itemPO.skuId.toLong(),
            itemPO.skuVersion,
            itemPO.count,
            Price(itemPO.unitPrice),
            Price(itemPO.totalPrice)
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
                saleOrder.positiveStatus?.name?.also { positiveStatus = it }
                saleOrder.reverseStatus?.name?.also { reverseStatus = it }
                saleOrder.amount.getBasicValue().also { amount = it }
                saleOrder.actualPay.getBasicValue().also { actualPay = it }
            }

            saleOrderItemPOs = saleOrder.orderItems.map {
                SaleOrderItemPO().apply {
                    it.id?.also { saleOrderId = it.value }
                    it.count.also { count = it }
                    it.skuId.also { skuId = it.toString() }
                    it.spuId.also { spuId = it.toString() }
                    it.skuVersion.also { skuVersion = it }
                    it.unitPrice.also { unitPrice = it.getBasicValue() }
                    it.totalPrice.also { totalPrice = it.getBasicValue() }
                }
            }
        }
    }
}