package com.jstore.fulfillment.domain

import com.jstore.fulfillment.domain.persistence.FulfillmentItemPO
import com.jstore.fulfillment.domain.persistence.FulfillmentOrderPO
import com.jstore.fulfillment.domain.persistence.FulfillmentOrderPOJpaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class FulfillmentOrderRepositoryImpl(private val jpaRepository: FulfillmentOrderPOJpaRepository) :
    FulfillmentOrderRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(entity: FulfillmentOrder): FulfillmentOrder =
        toDomain(jpaRepository.save(toPO(entity)))

    override fun findById(id: FulfillmentOrderId): FulfillmentOrder? =
        jpaRepository.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: Long): FulfillmentOrder? =
        jpaRepository.findByOrderId(orderId)?.let(::toDomain)

    private fun toPO(fulfillment: FulfillmentOrder) =
        FulfillmentOrderPO(
            id = fulfillment.id.value,
            orderId = fulfillment.orderId,
            merchantId = fulfillment.merchantId,
            status = fulfillment.status,
            recipientName = fulfillment.recipient.name,
            recipientPhone = fulfillment.recipient.phone,
            recipientEmail = fulfillment.recipient.email,
            countryCode = fulfillment.recipient.countryCode,
            districtCode = fulfillment.recipient.districtCode,
            detailAddress = fulfillment.recipient.detailAddress,
            carrierCode = fulfillment.carrierCode,
            trackingNumber = fulfillment.trackingNumber,
            items =
                fulfillment.items
                    .map {
                        FulfillmentItemPO(
                            id = it.orderItemId,
                            fulfillmentOrderId = fulfillment.id.value,
                            orderItemId = it.orderItemId,
                            skuId = it.skuId,
                            quantity = it.quantity,
                        )
                    }
                    .toMutableList(),
        )

    private fun toDomain(po: FulfillmentOrderPO): FulfillmentOrder =
        FulfillmentOrderImpl(
            id = FulfillmentOrderId(po.id),
            orderId = po.orderId,
            merchantId = po.merchantId,
            recipient =
                ShippingRecipient(
                    po.recipientName,
                    po.recipientPhone,
                    po.recipientEmail,
                    po.countryCode,
                    po.districtCode,
                    po.detailAddress,
                ),
            items = po.items.map { FulfillmentItem(it.orderItemId, it.skuId, it.quantity) },
            _status = po.status,
            _carrierCode = po.carrierCode,
            _trackingNumber = po.trackingNumber,
        )
}
