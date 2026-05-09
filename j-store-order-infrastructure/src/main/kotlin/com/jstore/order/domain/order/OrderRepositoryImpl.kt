package com.jstore.order.domain.order

import com.jstore.common.framework.Page
import com.jstore.common.framework.SortedPage
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.properties.Price
import com.jstore.order.domain.order.persistence.RecipientInfoPO
import com.jstore.order.domain.order.persistence.OrderItemPO
import com.jstore.order.domain.order.persistence.OrderPO
import com.jstore.order.domain.order.persistence.OrderPOJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Repository

@Repository
class OrderRepositoryImpl(
    private val jpaRepository: OrderPOJpaRepository,
) : OrderRepository {

    override fun add(order: Order) {
        val po = Converter.toPO(order)
        jpaRepository.save(po)
    }

    override fun save(entity: Order): Order {
        val po = Converter.toPO(entity)
        val saved = jpaRepository.save(po)
        return Converter.toDomain(saved)
    }

    override fun findById(id: OrderId): Order? {
        return jpaRepository.findById(id.value).orElse(null)?.let { Converter.toDomain(it) }
    }

    override fun findByBuyerUserId(uid: Long): List<Order> {
        return jpaRepository.findByBuyerUid(uid).map { Converter.toDomain(it) }
    }

    override fun pageListByUserId(uid: Long, currentPage: Int, pageSize: Int): Page<Order> {
        val pageable = PageRequest.of(currentPage - 1, pageSize, Sort.by(Sort.Direction.DESC, "createTime"))
        val page = jpaRepository.findByBuyerUid(uid, pageable)
        return SortedPage(
            current = currentPage,
            size = page.totalElements.toInt(),
            record = page.content.map { Converter.toDomain(it) }
        )
    }

    internal object Converter {

        fun toPO(order: Order): OrderPO {
            val si = order.recipientInfo
            val recipientInfoPO = RecipientInfoPO(
                consigneeName = si.name,
                consigneePhone = si.contractInfo.phoneNumber?.value,
                consigneeEmail = si.contractInfo.email,
                countryCode = si.shippingAddress.countryCode.value,
                districtCode = si.shippingAddress.getLeafCode(),
                shippingAddress = si.shippingAddress,
                detailAddress = si.shippingDetailAddress,
            )
            return OrderPO(
                id = order.id.value,
                buyerUid = order.buyerInfo.uid,
                buyerPhone = order.buyerInfo.phoneNumber?.value,
                buyerName = order.buyerInfo.userName,
                recipientInfo = recipientInfoPO,
                status = order.status,
                previousStatus = order.previousStatus,
                totalAmount = order.totalAmount.toBigDecimal(),
                actualPay = order.actualPay.toBigDecimal(),
                createTime = order.createTime,
                updateTime = order.updateTime,
                items = order.items.map { toItemPO(it, order.id.value) }.toMutableList(),
            )
        }

        fun toItemPO(item: OrderItem, orderId: Long): OrderItemPO {
            return OrderItemPO(
                id = item.id.value,
                orderId = orderId,
                skuId = item.skuId,
                spuId = item.spuId,
                goodsName = item.goodsName,
                skuDescription = item.skuDescription,
                quantity = item.quantity,
                unitPrice = item.unitPrice.toBigDecimal(),
                snapshotVersion = item.snapshotVersion,
                status = item.status,
                previousItemStatus = item.previousItemStatus,
            )
        }

        fun toDomain(po: OrderPO): Order {
            val items = po.items.map { toDomainItem(it) }.toMutableList()
            val recipientInfoPo = po.recipientInfo
                ?: error("Order ${po.id} has no consignee_info")

            val address = recipientInfoPo.shippingAddress
                ?: error("Order ${po.id} consignee_info has no shippingAddress")

            val contractInfo = ContractInfo(
                email = recipientInfoPo.consigneeEmail,
                phoneNumber = recipientInfoPo.consigneePhone?.let { PhoneNumber(it) },
            )

            val consignInfo = RecipientInfo(
                name = recipientInfoPo.consigneeName ?: "",
                contractInfo = contractInfo,
                shippingAddress = address,
                shippingDetailAddress = recipientInfoPo.detailAddress,
            )

            return OrderImpl(
                id = OrderId(po.id),
                buyerInfo = UserInfo(
                    uid = po.buyerUid,
                    phoneNumber = po.buyerPhone?.let { PhoneNumber(it) },
                    userName = po.buyerName,
                ),
                _items = items.toMutableList(),

                recipientInfo = consignInfo,
                _status = po.status,
                totalAmount = Price.fromBigDecimal(po.totalAmount),
                _actualPay = Price.fromBigDecimal(po.actualPay),
                createTime = po.createTime,
                _updateTime = po.updateTime,
                _previousStatus = po.previousStatus,
            )
        }

        fun toDomainItem(po: OrderItemPO): OrderItem {
            return OrderItemImpl(
                id = OrderItemId(po.id),
                skuId = po.skuId,
                spuId = po.spuId,
                goodsName = po.goodsName,
                skuDescription = po.skuDescription,
                quantity = po.quantity,
                unitPrice = Price.fromBigDecimal(po.unitPrice),
                snapshotVersion = po.snapshotVersion,
                status = po.status,
                _previousItemStatus = po.previousItemStatus,
            )
        }
    }
}
