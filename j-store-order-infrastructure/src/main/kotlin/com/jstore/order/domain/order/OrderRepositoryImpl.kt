package com.jstore.order.domain.order

import com.jstore.common.errors.CommonErrors
import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.DomainEventRepository
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.common.utils.json.JsonUtils
import com.jstore.order.acl.GoodsId
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
    @Nullable
    private val domainEventRepository: DomainEventRepository?,
    private val domainEventPublisher: DomainEventPublisher,
    private val orderPOJpaRepository: OrderPOJpaRepository,
    private val orderItemPOJpaRepository: OrderItemPOJpaRepository,
) : OrderRepository {

    override fun findByBuyerUserId(uid: Long): List<Order> {
        val orderPOS = orderPOJpaRepository.findOrderPOSByUid(uid)
        if (orderPOS.isEmpty()) {
            return listOf()
        }
        val orderIdList = orderPOS.stream().map { o -> o.orderId }.toList()
        val orderItemPOS = orderItemPOJpaRepository.findAllByOrderIdIsIn(orderIdList)
        return OrderConverter.pos2Entities(orderPOS, orderItemPOS)
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
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
    override fun save(entity: Order): Order {
        (entity as? Order)?.let {
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

    override fun findById(id: OrderId): Order? {
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

    fun po2Entity(orderPO: OrderPO, orderItemPOList: Collection<OrderItemPO>): Order {
        val id = OrderId(orderPO.orderId)
        val buyerInfo = UserInfo(orderPO.uid, PhoneNumber(orderPO.phoneNumber), orderPO.userName)
        val items: List<OrderItem> = orderItemPOList.map { orderItemPO2Entity(it) }.toList()
        val addressInfo: GeoAddressInfo = json2AddressInfo(orderPO.addressInfo)

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
    ): List<Order> {
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
            totalPrice = Price(itemPO.totalPrice),
            itemStatus = itemPO.itemStatus
        )
    }


    fun entity2POHolder(order: Order): OrderPOHolder {
        return OrderPOHolder().apply {
            orderPO = OrderPO(
                orderId = order.id.value,
                uid = order.buyerInfo.uid,
                phoneNumber = order.buyerInfo.phoneNumber?.value ?: "",
                userName = order.buyerInfo.userName ?: "",
                addressInfo = AddressInfo2JsonString(order.deliveryAddressInfo),
                positiveStatus = order.status.name,
                amount = order.amount.getBasicValue(),
                actualPay = order.actualPay.getBasicValue(),
                createTime = order.createTime,
                updateTime = order.updateTime
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
