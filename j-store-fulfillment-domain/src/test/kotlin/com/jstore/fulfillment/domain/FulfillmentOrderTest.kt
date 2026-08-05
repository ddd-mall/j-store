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
package com.jstore.fulfillment.domain

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.fulfillment.domain.event.FulfillmentPreparedEvent
import com.jstore.fulfillment.domain.event.ShipmentDeliveredEvent
import com.jstore.fulfillment.domain.event.ShipmentDispatchedEvent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class FulfillmentOrderTest :
    FunSpec({
        fun fulfillment() =
            FulfillmentOrderImpl(
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
            fulfillment.pendingDomainEvents().map { it::class } shouldBe
                listOf(
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
