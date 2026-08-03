package com.jstore.order.domain.order

import com.jstore.order.domain.order.persistence.OrderPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

class OrderPOStatusMappingTest : FunSpec({
    test("OrderPO maps exactly three non-null string status columns") {
        mapOf(
            "tradeStatus" to "trade_status",
            "paymentStatus" to "payment_status",
            "fulfillmentStatus" to "fulfillment_status",
        ).forEach { (property, columnName) ->
            val field = OrderPO::class.java.getDeclaredField(property)
            field.getAnnotation(Enumerated::class.java).value shouldBe EnumType.STRING
            field.getAnnotation(Column::class.java).also {
                it.name shouldBe columnName
                it.nullable shouldBe false
                it.length shouldBe 32
            }
        }
        val removedPreviousStatusProperty = "previous" + "Status"
        OrderPO::class.java.declaredFields.none { it.name == "status" || it.name == removedPreviousStatusProperty } shouldBe true
    }
})
