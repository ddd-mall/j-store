package com.jstore.payment.service

import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.properties.Price
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.CreatePaymentForOrderCommand
import com.jstore.contracts.commerce.RequestPaymentRefundCommand
import com.jstore.payment.domain.payment.PaymentRefundItem

class CreatePaymentForOrderCommandHandler(private val payments: PaymentUseCase) :
    IntegrationMessageHandler<CreatePaymentForOrderCommand> {
    override fun handlerId() = "payment.create-for-order.v1"

    override fun handle(message: CreatePaymentForOrderCommand) {
        payments
            .createForOrder(
                PaymentOrderRequest(
                    message.orderId,
                    message.merchantId,
                    Price.ofFen(message.payableAmountFen),
                    message.currency,
                )
            )
            .getOrThrow()
    }
}

class RequestPaymentRefundCommandHandler(private val payments: PaymentUseCase) :
    IntegrationMessageHandler<RequestPaymentRefundCommand> {
    override fun handlerId() = "payment.request-refund.v1"

    override fun handle(message: RequestPaymentRefundCommand) {
        payments
            .requestRefund(
                PaymentRefundRequest(
                    message.orderId,
                    message.afterSaleId,
                    message.items.map {
                        PaymentRefundItem(
                            it.orderItemId,
                            it.skuId,
                            it.quantity,
                            Price.ofFen(it.amountFen),
                        )
                    },
                    Price.ofFen(message.amountFen),
                ),
                message.occurredAt,
            )
            .getOrThrow()
    }
}
