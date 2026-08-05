package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessErrorException
import com.jstore.common.framework.messaging.IntegrationMessageHandler
import com.jstore.common.utils.getOrThrow
import com.jstore.contracts.commerce.CreateFulfillmentForOrderCommand
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.ShippingRecipient

class CreateFulfillmentForOrderCommandHandler(private val fulfillments: FulfillmentUseCase) :
    IntegrationMessageHandler<CreateFulfillmentForOrderCommand> {
    override fun handlerId() = "fulfillment.create-for-order.v1"

    override fun handle(message: CreateFulfillmentForOrderCommand) {
        fulfillments
            .createForOrder(
                FulfillmentRequest(
                    orderId = message.orderId,
                    merchantId = message.merchantId,
                    recipient =
                        ShippingRecipient(
                            message.recipient.name,
                            message.recipient.phone,
                            message.recipient.email,
                            message.recipient.countryCode,
                            message.recipient.districtCode,
                            message.recipient.detailAddress,
                        ),
                    items =
                        message.items.map {
                            FulfillmentItem(
                                requireNotNull(it.orderItemId),
                                it.skuId,
                                it.quantity,
                            )
                        },
                )
            )
            .getOrThrow(::BusinessErrorException)
    }
}
