package com.jstore.fulfillment.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.persistent.SnowFlakSequence
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.getOrThrow
import com.jstore.common.utils.onFailure
import com.jstore.fulfillment.domain.FulfillmentErrors
import com.jstore.fulfillment.domain.FulfillmentItem
import com.jstore.fulfillment.domain.FulfillmentOrder
import com.jstore.fulfillment.domain.FulfillmentOrderId
import com.jstore.fulfillment.domain.FulfillmentOrderImpl
import com.jstore.fulfillment.domain.FulfillmentOrderRepository
import com.jstore.fulfillment.domain.ShippingRecipient
import java.time.Instant

data class FulfillmentRequest(
    val orderId: Long,
    val merchantId: Long,
    val recipient: ShippingRecipient,
    val items: List<FulfillmentItem>,
)

class FulfillmentApplicationService(
    private val repository: FulfillmentOrderRepository,
    private val sequence: SnowFlakSequence,
    private val publisher: DomainEventPublisher,
) : FulfillmentUseCase {
    override fun createForOrder(request: FulfillmentRequest): Result<FulfillmentOrder, BusinessError> {
        repository.findByOrderId(request.orderId)?.let { existing ->
            return if (
                existing.merchantId == request.merchantId &&
                    existing.recipient == request.recipient &&
                    existing.items == request.items
            )
                Success(existing)
            else Failure(FulfillmentErrors.ORDER_CONFLICT)
        }
        val fulfillment =
            FulfillmentOrderImpl(
                id = FulfillmentOrderId(sequence.nextId()),
                orderId = request.orderId,
                merchantId = request.merchantId,
                recipient = request.recipient,
                items = request.items,
            )
        repository.save(fulfillment)
        fulfillment.publishPendingEvents(publisher)
        return Success(fulfillment)
    }

    override fun getByOrderId(orderId: Long): Result<FulfillmentOrder, BusinessError> =
        repository.findByOrderId(orderId)?.let(::Success) ?: Failure(FulfillmentErrors.NOT_FOUND)

    override fun prepare(
        orderId: Long,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = mutate(orderId) { it.prepare(occurredAt) }

    override fun dispatch(
        orderId: Long,
        carrierCode: String,
        trackingNumber: String,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> =
        mutate(orderId) {
            it.dispatch(carrierCode, trackingNumber, occurredAt)
        }

    override fun deliver(
        orderId: Long,
        occurredAt: Instant,
    ): Result<Boolean, BusinessError> = mutate(orderId) { it.deliver(occurredAt) }

    private fun mutate(
        orderId: Long,
        operation: (FulfillmentOrder) -> Result<Boolean, BusinessError>,
    ): Result<Boolean, BusinessError> {
        val fulfillment =
            repository.findByOrderId(orderId) ?: return Failure(FulfillmentErrors.NOT_FOUND)
        val changed = operation(fulfillment)
        changed.onFailure {
            return Failure(it)
        }
        if (changed.getOrThrow()) {
            repository.save(fulfillment)
            fulfillment.publishPendingEvents(publisher)
        }
        return changed
    }
}
