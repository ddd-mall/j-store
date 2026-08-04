package com.jstore.payment.domain.payment

import com.jstore.common.properties.Price
import com.jstore.payment.domain.payment.persistence.PaymentOrderPO
import com.jstore.payment.domain.payment.persistence.PaymentOrderPOJpaRepository
import com.jstore.payment.domain.payment.persistence.PaymentRefundPO
import com.jstore.payment.domain.payment.persistence.PaymentRefundItemPO
import org.springframework.stereotype.Repository

@Repository
class PaymentOrderRepositoryImpl(
    private val jpaRepository: PaymentOrderPOJpaRepository,
) : PaymentOrderRepository {
    override fun save(entity: PaymentOrder): PaymentOrder = toDomain(jpaRepository.save(toPO(entity)))

    override fun findById(id: PaymentOrderId): PaymentOrder? =
        jpaRepository.findById(id.value).orElse(null)?.let(::toDomain)

    override fun findByOrderId(orderId: Long): PaymentOrder? =
        jpaRepository.findByOrderId(orderId)?.let(::toDomain)

    override fun findByRefundId(refundId: PaymentRefundId): PaymentOrder? =
        jpaRepository.findByRefundId(refundId.value)?.let(::toDomain)

    private fun toPO(payment: PaymentOrder) = PaymentOrderPO(
        id = payment.id.value,
        orderId = payment.orderId,
        merchantId = payment.merchantId,
        payableAmount = payment.payableAmount.toBigDecimal(),
        currency = payment.currency,
        status = payment.status,
        providerTransactionId = payment.capture?.providerTransactionId,
        capturedAmount = payment.capture?.amount?.toBigDecimal(),
        capturedAt = payment.capture?.capturedAt,
        refunds = payment.refunds.map {
            PaymentRefundPO(
                id = it.id.value,
                paymentOrderId = payment.id.value,
                afterSaleId = it.afterSaleId,
                amount = it.amount.toBigDecimal(),
                status = it.status,
                providerRefundId = it.providerRefundId,
                failureReason = it.failureReason,
                requestedAt = it.requestedAt,
                completedAt = it.completedAt,
                items = it.items.map { item ->
                    PaymentRefundItemPO(
                        id = "${it.id.value}:${item.orderItemId}",
                        paymentRefundId = it.id.value,
                        orderItemId = item.orderItemId,
                        skuId = item.skuId,
                        quantity = item.quantity,
                        amount = item.amount.toBigDecimal(),
                    )
                }.toMutableList(),
            )
        }.toMutableList(),
    )

    private fun toDomain(po: PaymentOrderPO): PaymentOrder = PaymentOrderImpl(
        id = PaymentOrderId(po.id),
        orderId = po.orderId,
        merchantId = po.merchantId,
        payableAmount = Price.fromBigDecimal(po.payableAmount),
        currency = po.currency,
        _status = po.status,
        _capture = po.providerTransactionId?.let { transactionId ->
            PaymentCapture(transactionId, Price.fromBigDecimal(requireNotNull(po.capturedAmount)), requireNotNull(po.capturedAt))
        },
        _refunds = po.refunds.map {
            PaymentRefund(
                id = PaymentRefundId(it.id),
                afterSaleId = it.afterSaleId,
                items = it.items.map { item ->
                    PaymentRefundItem(
                        item.orderItemId,
                        item.skuId,
                        item.quantity,
                        Price.fromBigDecimal(item.amount),
                    )
                },
                amount = Price.fromBigDecimal(it.amount),
                status = it.status,
                providerRefundId = it.providerRefundId,
                failureReason = it.failureReason,
                requestedAt = it.requestedAt,
                completedAt = it.completedAt,
            )
        }.toMutableList(),
    )
}
