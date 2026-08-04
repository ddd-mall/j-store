package com.jstore.fulfillment.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class FulfillmentOrderTest : FunSpec({
    fun fulfillment() = FulfillmentOrderImpl(
        FulfillmentOrderId(1),
        10,
        20,
        ShippingRecipient("buyer", "13800138000", null, "CN", "110101", "street"),
        listOf(FulfillmentItem(30, 40, 2)),
    )

    test("fulfillment follows prepare dispatch deliver sequence") {
        val fulfillment = fulfillment()
        (fulfillment.dispatch("SF", "123", Instant.EPOCH) is Failure) shouldBe true
        (fulfillment.prepare(Instant.EPOCH) as Success).value shouldBe true
        (fulfillment.dispatch("sf", "123", Instant.EPOCH) as Success).value shouldBe true
        (fulfillment.deliver(Instant.EPOCH) as Success).value shouldBe true

        fulfillment.status shouldBe FulfillmentOrderStatus.DELIVERED
        fulfillment.carrierCode shouldBe "SF"
        fulfillment.domainEventQueue.map { it::class } shouldBe listOf(
            FulfillmentPreparedEvent::class,
            ShipmentDispatchedEvent::class,
            ShipmentDeliveredEvent::class,
        )
    }

    test("replayed dispatch with the same carrier reference is idempotent") {
        val fulfillment = fulfillment()
        fulfillment.prepare(Instant.EPOCH)
        fulfillment.dispatch("SF", "123", Instant.EPOCH)

        (fulfillment.dispatch("SF", "123", Instant.EPOCH) as Success).value shouldBe false
        (fulfillment.dispatch("YT", "999", Instant.EPOCH) is Failure) shouldBe true
    }
})
