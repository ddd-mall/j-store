package com.jstore.com.jstore.order.domain.order

import com.jstore.com.jstore.order.acl.geo.address.GeoAddressServiceProxy
import com.jstore.com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.com.jstore.order.domain.order.persistence.OrderItemPOJpaRepository
import com.jstore.com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.DomainEventRepository
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.*
import com.jstore.order.domain.order.GeoAddressInfo
import com.jstore.order.domain.order.UserInfo
import com.jstore.order.domain.acl.GoodsId
import jakarta.transaction.Transactional
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.lang.Nullable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime


@Repository
class OrderRepositoryImpl(
    @Nullable
    private val domainEventRepository: DomainEventRepository?,
    private val domainEventPublisher: DomainEventPublisher,
    private val orderPOJpaRepository: OrderPOJpaRepository,
    private val orderItemPOJpaRepository: OrderItemPOJpaRepository,
) : OrderRepository {

    override fun findByBuyerUserId(uid: Long): List<com.jstore.order.domain.order.Order> {
        val orderPOS = orderPOJpaRepository.findOrderPOSByUid(uid)
        if (orderPOS.isEmpty()) {
            return listOf()
        }
        val orderIdList = orderPOS.stream().map { o -> o.orderId }.toList()
        val orderItemPOS = orderItemPOJpaRepository.findAllByOrderIdIsIn(orderIdList)
        return OrderConverter.pos2Entities(orderPOS, orderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<com.jstore.order.domain.order.Order> {
        val orderPOPage = orderPOJpaRepository.findAllByUidOrderByCreateTimeDesc(
            uid,
            PageRequest.of(currentPage, pageSize, Sort.by(listOf(Order.desc("create_time"))))
        )
        val orderPOS = orderPOPage.get().toList()
        val orderItemPOList = orderPOPage.get().map { it.orderId }.toList().let { orderIds ->
            orderItemPOJpaRepository.findAllByOrderIdIsIn(orderIds)
        }
        return SortedPage(currentPage, pageSize, OrderConverter.pos2Entities(orderPOS, orderItemPOList))
    }

    @Transactional(rollbackOn = [Exception::class])
    override fun save(entity: com.jstore.order.domain.order.Order): com.jstore.order.domain.order.Order {
        (entity as? com.jstore.order.domain.order.Order)?.let {
            val holder: OrderPOHolder = OrderConverter.entity2POHolder(entity)

            val savedOrderPO = holder.orderPO.let {
                orderPOJpaRepository.save(it.apply { updateTime = LocalDateTime.now() })
            }
            val savedOrderItemPOs = if (holder.orderItemPOS.isNotEmpty()) {
                orderItemPOJpaRepository.saveAll(holder.orderItemPOS)
            } else {
                listOf()
            }
            val unpublishedDomainEvent = entity.getUnpublishedDomainEvent()
            unpublishedDomainEvent.forEach(domainEventPublisher::publishEvent)
            domainEventRepository?.saveAll(unpublishedDomainEvent)
            return OrderConverter.po2Entity(savedOrderPO, savedOrderItemPOs)
        }
        throw CommonErrors.INVALID_PARAM
    }

    override fun findById(id: OrderId): com.jstore.order.domain.order.Order? {
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

    fun po2Entity(orderPO: OrderPO, orderItemPOList: Collection<OrderItemPO>): com.jstore.order.domain.order.Order {
        val id = OrderId(orderPO.orderId)
        val buyerInfo = UserInfo(orderPO.uid, PhoneNumber(orderPO.phoneNumber), orderPO.userName)
        val items: List<OrderItem> = orderItemPOList.map { orderItemPO2Entity(it) }.toList()
        val addressInfo: GeoAddressInfo = GeoAddressServiceProxy.getByDistrictCode(orderPO.districtCode)
            .apply { detailAddress = orderPO.detailAddress }

        return Order(
            id = id,
            buyerInfo = buyerInfo,
            orderItems = items,
            deliveryAddressInfo = addressInfo,
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
    ): List<com.jstore.order.domain.order.Order> {
        val itemMap: Map<Long, List<OrderItemPO>> = orderItemPOS.groupBy { item -> item.orderId }
        return orderPOS.map { orderPO ->
            po2Entity(
                orderPO = orderPO,
                orderItemPOList = itemMap[orderPO.orderId] ?: listOf()
            )
        }
    }

    private fun orderItemPO2Entity(itemPO: OrderItemPO): OrderItem {
        return OrderItem(
            id = OrderItemId(itemPO.orderItemId),
            goodsId = GoodsId(itemPO.spuId.toLong(), itemPO.skuId.toLong()),
            goodsVersion = itemPO.goodsVersion,
            quantity = itemPO.quantity,
            unitPrice = Price(itemPO.unitPrice),
            totalPrice = Price(itemPO.totalPrice)
        )
    }


    fun entity2POHolder(order: com.jstore.order.domain.order.Order): OrderPOHolder {
        return OrderPOHolder().apply {
            orderPO = OrderPO(
                orderId = order.id.value,
                uid = order.buyerInfo.uid,
                phoneNumber = order.buyerInfo.phoneNumber?.value ?: "",
                userName = order.buyerInfo.userName ?: "",
                districtCode = order.deliveryAddressInfo.districtCode,
                detailAddress = order.deliveryAddressInfo.detailAddress ?: "",
                positiveStatus = order.status.name,
                amount = order.amount.getBasicValue(),
                actualPay = order.actualPay.getBasicValue(),
            )

            orderItemPOS = order.orderItems.map {
                OrderItemPO(
                    orderItemId = it.id.value,
                    orderId = order.id.value,
                    quantity = it.quantity,
                    skuId = it.goodsId.skuId.toString(),
                    spuId = it.goodsId.spuId.toString(),
                    goodsVersion = it.goodsVersion,
                    unitPrice = it.unitPrice.getBasicValue(),
                    totalPrice = it.totalPrice.getBasicValue(),
                )
            }
        }
    }
}
