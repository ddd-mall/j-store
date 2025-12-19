package com.jstore.order.domain.order

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.DomainEventRepository
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.order.domain.order.item.NormalItem
import com.jstore.order.domain.order.item.OrderItemId
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderItemPOJpaRepository
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.lang.Nullable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime


@Repository
class OrderRepositoryImpl(
    @param:Nullable
    private val domainEventRepository: DomainEventRepository?,
    private val domainEventPublisher: DomainEventPublisher,
    private val orderPOJpaRepository: OrderPOJpaRepository,
    private val orderItemPOJpaRepository: OrderItemPOJpaRepository,
) : OrderRepository {

    override fun findByBuyerUserId(uid: Long): List<NormalOrderImpl> {
        val orderPOS = orderPOJpaRepository.findOrderPOSByUid(uid)
        if (orderPOS.isEmpty()) {
            return listOf()
        }
        val orderIdList = orderPOS.stream().map { o -> o.orderId }.toList()
        val orderItemPOS = orderItemPOJpaRepository.findAllByOrderIdIsIn(orderIdList)
        return OrderConverter.pos2Entities(orderPOS, orderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<NormalOrderImpl> {
        val orderPOPage = orderPOJpaRepository.findAllByUidOrderByCreateTimeDesc(
            uid,
            PageRequest.of(currentPage, pageSize, Sort.by(listOf(Sort.Order.desc("create_time"))))
        )
        val orderPOS = orderPOPage.get().toList()
        val orderItemPOList = orderPOPage.get().map { it.orderId }.toList().let { orderIds ->
            orderItemPOJpaRepository.findAllByOrderIdIsIn(orderIds)
        }
        return SortedPage(currentPage, pageSize, OrderConverter.pos2Entities(orderPOS, orderItemPOList))
    }

    @Transactional(rollbackOn = [Exception::class])
    override fun save(entity: NormalOrderImpl): NormalOrderImpl {
        val holder: OrderPOHolder = OrderConverter.entity2POHolder(entity)

        holder.orderPO.updateTime = LocalDateTime.now()
        val savedOrderPO = orderPOJpaRepository.save(holder.orderPO)

        val savedOrderItemPOs = if (holder.orderItemPOS.isNotEmpty()) {
            orderItemPOJpaRepository.saveAll(holder.orderItemPOS)
        } else {
            listOf()
        }
        val unpublishedDomainEvent = entity.getDomainEvent()
        domainEventRepository?.saveAll(unpublishedDomainEvent)
        unpublishedDomainEvent.forEach(domainEventPublisher::publishEvent)
        return OrderConverter.po2Entity(savedOrderPO, savedOrderItemPOs)
    }

    override fun findById(id: OrderId): NormalOrderImpl? {
        orderPOJpaRepository.findByIdOrNull(id.value)?.let { orderPO ->
            orderItemPOJpaRepository.findAllByOrderId(id.value).let { orderItemPOs ->
                return OrderConverter.po2Entity(orderPO, orderItemPOs)
            }
        }
        return null
    }
}

open class OrderPOHolder {
    lateinit var orderPO: OrderPO
    var orderItemPOS: Collection<OrderItemPO> = listOf()
}


object OrderConverter {

    fun po2Entity(orderPO: OrderPO, orderItemPOList: Collection<OrderItemPO>): NormalOrderImpl {
        val id = OrderId(orderPO.orderId)
        val buyerInfo = UserInfo(orderPO.uid, PhoneNumber(orderPO.phoneNumber), orderPO.userName)
        val items: List<NormalItem> = orderItemPOList.map { orderItemPO2Entity(it) }.toList()
        val addressInfo: GeoAddressInfo = json2AddressInfo(orderPO.addressInfo)

        return NormalOrderImpl(
            id = id,
            buyerInfo = buyerInfo,
            orderItemImpls = items,
            shippingAddressInfo = addressInfo,
            status = OrderStatus.valueOf(orderPO.positiveStatus),
            amount = Price(orderPO.amount),
            actualPay = Price(orderPO.actualPay),
            createTime = orderPO.createTime,
            updateTime = orderPO.updateTime
        )
    }



    fun pos2Entities(
        orderPOS: Collection<OrderPO>,
        orderItemPOS: Collection<OrderItemPO>,
    ): List<NormalOrderImpl> {
        val itemMap: Map<Long, List<OrderItemPO>> = orderItemPOS.groupBy { item -> item.orderId }
        return orderPOS.map { orderPO ->
            po2Entity(
                orderPO = orderPO,
                orderItemPOList = itemMap[orderPO.orderId] ?: listOf()
            )
        }
    }

    private fun orderItemPO2Entity(itemPO: OrderItemPO): NormalItem {
        return NormalItem(
            id = OrderItemId(itemPO.orderItemId),
            quantity = itemPO.quantity,
            unitPrice = Price(itemPO.unitPrice),
            totalPrice = Price(itemPO.totalPrice),
            itemStatus = itemPO.itemStatus
        )
    }


    fun entity2POHolder(normalOrderImpl: NormalOrderImpl): OrderPOHolder {
        return OrderPOHolder().apply {
            orderPO = OrderPO(
                orderId = normalOrderImpl.id.value,
                uid = normalOrderImpl.buyerInfo.uid,
                phoneNumber = normalOrderImpl.buyerInfo.phoneNumber?.value ?: "",
                userName = normalOrderImpl.buyerInfo.userName ?: "",
                addressInfo = AddressInfo2JsonString(normalOrderImpl.shippingAddressInfo),
                positiveStatus = normalOrderImpl.status.name,
                amount = normalOrderImpl.amount.getBasicValue(),
                actualPay = normalOrderImpl.actualPay.getBasicValue(),
                createTime = normalOrderImpl.createTime,
                updateTime = normalOrderImpl.updateTime
            )

            orderItemPOS = normalOrderImpl.orderItemImpls.map {
                OrderItemPO(
                    orderItemId = it.id.value,
                    orderId = normalOrderImpl.id.value,
                    quantity = it.quantity,
                    unitPrice = it.unitPrice.getBasicValue(),
                    totalPrice = it.totalPrice.getBasicValue(),
                    itemStatus = it.itemStatus,
                )
            }
        }
    }

    private fun json2AddressInfo(jsonString: String): GeoAddressInfo {
        return JsonUtils.deserialize<GeoAddressInfo>(jsonString)
    }

    private fun AddressInfo2JsonString(addressInfo: GeoAddressInfo): String {
        return JsonUtils.toJsonString(addressInfo)
    }
}
