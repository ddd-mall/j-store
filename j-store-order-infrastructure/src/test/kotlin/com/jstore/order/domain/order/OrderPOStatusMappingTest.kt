/*
 * SPDX-FileCopyrightText: 2024-2026 潘少峰 (Peter Pan)
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jstore.order.domain.order

import com.jstore.order.domain.order.persistence.OrderPO
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.Column
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated

class OrderPOStatusMappingTest :
    FunSpec({
        test("OrderPO maps exactly three non-null string status columns") {
            mapOf(
                    "tradeStatus" to "trade_status",
                    "paymentStatus" to "payment_status",
                    "fulfillmentStatus" to "fulfillment_status",
                )
                .forEach { (property, columnName) ->
                    val field = OrderPO::class.java.getDeclaredField(property)
                    field.getAnnotation(Enumerated::class.java).value shouldBe EnumType.STRING
                    field.getAnnotation(Column::class.java).also {
                        it.name shouldBe columnName
                        it.nullable shouldBe false
                        it.length shouldBe 32
                    }
                }
            val removedPreviousStatusProperty = "previous" + "Status"
            OrderPO::class.java.declaredFields.none {
                it.name == "status" || it.name == removedPreviousStatusProperty
            } shouldBe true
        }
    })
