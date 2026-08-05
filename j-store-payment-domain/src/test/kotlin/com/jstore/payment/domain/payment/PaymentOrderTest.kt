package com.jstore.payment.domain.payment

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.payment.domain.payment.event.PaymentCapturedEvent
import com.jstore.payment.domain.payment.event.PaymentRefundSucceededEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PaymentOrderTest :
    FunSpec({
        fun payment() = PaymentOrderImpl(PaymentOrderId(1), 10, 20, Price.ofFen(1_000), "CNY")

        test("capture requires the frozen full amount and is idempotent by provider transaction") {
            val payment = payment()
            (payment.capture("txn-1", Price.ofFen(900), "CNY", Instant.EPOCH) is Failure) shouldBe
                true
            payment.status shouldBe PaymentOrderStatus.PENDING

            (payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH) as Success)
                .value shouldBe true
            payment.status shouldBe PaymentOrderStatus.CAPTURED
            payment.pendingDomainEvents().single()::class shouldBe PaymentCapturedEvent::class
            (payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH) as Success)
                .value shouldBe false
        }

        test("refund success is separate from refund request and updates payment status") {
            val payment = payment()
            payment.capture("txn-1", Price.ofFen(1_000), "CNY", Instant.EPOCH)
            payment.acknowledgeDomainEvents(
                payment.pendingDomainEvents().mapTo(linkedSetOf()) { it.eventId }
            )
            val refund =
                PaymentRefund(
                    PaymentRefundId(2),
                    30,
                    listOf(PaymentRefundItem(40, 50, 1, Price.ofFen(400))),
                    Price.ofFen(400),
                    requestedAt = Instant.EPOCH,
                )

            payment.requestRefund(refund, Instant.EPOCH)
            payment.status shouldBe PaymentOrderStatus.CAPTURED
            payment.markRefundSucceeded(refund.id, "provider-refund-1", Instant.EPOCH)

            payment.status shouldBe PaymentOrderStatus.PARTIALLY_REFUNDED
            payment.refunds.single().status shouldBe PaymentRefundStatus.SUCCEEDED
            payment.pendingDomainEvents().last()::class shouldBe PaymentRefundSucceededEvent::class
        }
    })
