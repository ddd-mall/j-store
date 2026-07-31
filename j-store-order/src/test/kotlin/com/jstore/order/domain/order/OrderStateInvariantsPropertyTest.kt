package com.jstore.order.domain.order

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.string.shouldContain
import io.kotest.assertions.throwables.shouldThrow

class OrderStateInvariantsPropertyTest : FunSpec({
    test("all enum combinations are accepted only when every declared invariant holds") {
        for (trade in TradeStatus.entries) for (payment in PaymentStatus.entries)
            for (fulfillment in FulfillmentStatus.entries) for (afterSale in AfterSaleStatus.entries) {
                val state = OrderStateSnapshot(trade, payment, fulfillment, afterSale, listOf(OrderItemStatus.NONE))
                val violations = OrderStateInvariants.violations(state)
                if (trade == TradeStatus.CREATED && payment == PaymentStatus.UNPAID &&
                    fulfillment == FulfillmentStatus.UNFULFILLED && afterSale == AfterSaleStatus.NONE
                ) violations.shouldBeEmpty()
            }
    }

    test("violations collects multiple failures and requireValid reports all") {
        val state = OrderStateSnapshot(
            TradeStatus.CREATED,
            PaymentStatus.REFUNDED,
            FulfillmentStatus.DELIVERED,
            AfterSaleStatus.NONE,
            emptyList(),
        )
        val violations = OrderStateInvariants.violations(state)
        violations.shouldNotBeEmpty()
        violations.size shouldBeGreaterThan 1
        val error = shouldThrow<IllegalArgumentException> { OrderStateInvariants.requireValid(state) }
        violations.forEach { error.message.orEmpty() shouldContain it }
    }
})

private infix fun Int.shouldBeGreaterThan(other: Int) {
    if (this <= other) error("Expected $this to be greater than $other")
}
