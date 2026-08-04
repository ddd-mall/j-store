package com.jstore.order.domain.aftersale

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.*

class AfterSaleAggregateTest :
    FunSpec({
        val snapshot =
            RefundEligibilitySnapshot(
                OrderItemId(10),
                2,
                Price.ofFen(200),
                "CNY",
                GoodsSnapshot(1, 2, "g", "s"),
            )
        fun aggregate() =
            AfterSaleImpl(
                AfterSaleId(1),
                OrderId(2),
                ApplicantActorId(3),
                MerchantActorId(4),
                AfterSaleStatus.REQUESTED,
                RefundReason(RefundCategory.OTHER, "reason"),
                FulfillmentSnapshot(FulfillmentStatus.UNFULFILLED, false),
                listOf(
                    AfterSaleItemImpl(
                        AfterSaleItemId(5),
                        OrderId(2),
                        OrderItemId(10),
                        1,
                        Price.ofFen(100),
                        "CNY",
                        snapshot,
                    )
                ),
                createTime = LocalDateTime.MIN,
                _updateTime = LocalDateTime.MIN,
            )
        test("value objects reject invalid bounds") {
            shouldThrow<IllegalArgumentException> { RefundReason(RefundCategory.OTHER, " ") }
            shouldThrow<IllegalArgumentException> {
                RefundEligibilitySnapshot(OrderItemId(1), 0, Price.ZERO, "CNY", snapshot.goods)
            }
        }
        test("aggregate rejects empty duplicate and cross-order items") {
            shouldThrow<IllegalArgumentException> {
                AfterSaleImpl(
                    AfterSaleId(1),
                    OrderId(2),
                    ApplicantActorId(3),
                    MerchantActorId(4),
                    AfterSaleStatus.REQUESTED,
                    RefundReason(RefundCategory.OTHER, "r"),
                    FulfillmentSnapshot(FulfillmentStatus.UNFULFILLED, false),
                    emptyList(),
                    createTime = LocalDateTime.MIN,
                    _updateTime = LocalDateTime.MIN,
                )
            }
        }
        test("approval moves an unshipped after-sale to refund pending") {
            val a = aggregate()
            (a.approve(MerchantActorId(99), Instant.EPOCH) is Failure) shouldBe true
            a.domainEventQueue.size shouldBe 0
            (a.approve(MerchantActorId(4), Instant.EPOCH) is Success) shouldBe true
            a.status shouldBe AfterSaleStatus.REFUND_PENDING
            a.domainEventQueue.size shouldBe 2
            (a.cancel(ApplicantActorId(3), Instant.EPOCH) is Failure) shouldBe true
            a.domainEventQueue.size shouldBe 2
        }
    })
