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
package com.jstore.cart.service

import com.jstore.cart.acl.CartCommerceFactsService
import com.jstore.cart.acl.OfferIdentity
import com.jstore.cart.domain.*
import com.jstore.common.framework.event.DomainEvent
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.Price
import com.jstore.common.utils.Success
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CartApplicationServiceTest {
    @Test
    fun `quantity mutation succeeds without synchronously collecting assessment facts`() {
        val carts = InMemoryCartRepository()
        val assessments = InMemoryCartAssessmentStore()
        val publisher = RecordingPublisher()
        val commerce = FixedCommerceFacts().apply { failWhenCollecting = true }
        var nextId = 10L
        val service =
            CartApplicationService(
                carts = carts,
                assessments = assessments,
                commerce = commerce,
                ids = CartIdentityGenerator { nextId++ },
                publisher = publisher,
                clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC),
                limitsProvider = CartLimitsProvider { CartLimits(1, 999, 100) },
            )

        val result =
            service.setItemQuantity(
                SetCartItemQuantityCommand(
                    buyerId = 7,
                    skuId = 101,
                    offerId = 201,
                    targetQuantity = 3,
                    expectedCartVersion = 0,
                )
            )

        val view = assertIs<Success<CartView>>(result).value
        assertEquals(1, view.contentVersion)
        assertEquals(null, view.assessment)
        assertEquals(0, commerce.collectCalls)
        assertEquals(1, carts.saveCount)
        assertEquals(1, publisher.events.size)
    }

    @Test
    fun `a stale retry at the requested quantity is a no-op without another event or save`() {
        val carts = InMemoryCartRepository()
        val assessments = InMemoryCartAssessmentStore()
        val publisher = RecordingPublisher()
        val commerce = FixedCommerceFacts()
        var nextId = 10L
        val service =
            CartApplicationService(
                carts = carts,
                assessments = assessments,
                commerce = commerce,
                ids = CartIdentityGenerator { nextId++ },
                publisher = publisher,
                clock = Clock.fixed(Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC),
                limitsProvider = CartLimitsProvider { CartLimits(1, 999, 100) },
            )
        val command =
            SetCartItemQuantityCommand(
                buyerId = 7,
                skuId = 101,
                offerId = 201,
                targetQuantity = 3,
                expectedCartVersion = 0,
            )

        val first = assertIs<Success<CartView>>(service.setItemQuantity(command)).value
        commerce.available = false
        val retry = assertIs<Success<CartView>>(service.setItemQuantity(command)).value

        assertEquals(1, first.contentVersion)
        assertEquals(1, retry.contentVersion)
        assertEquals(3, retry.lines.single().quantity)
        assertEquals(1, carts.saveCount)
        assertEquals(1, publisher.events.size)
    }

    @Test
    fun `automatic refresh propagates upstream failure for redelivery`() {
        val carts = InMemoryCartRepository()
        val commerce = FixedCommerceFacts().apply { failWhenCollecting = true }
        val publisher = RecordingPublisher()
        var nextId = 10L
        val service =
            CartApplicationService(
                carts,
                InMemoryCartAssessmentStore(),
                commerce,
                CartIdentityGenerator { nextId++ },
                publisher,
                limitsProvider = CartLimitsProvider { CartLimits(1, 999, 100) },
            )
        service.setItemQuantity(SetCartItemQuantityCommand(7, 101, 201, 3, 0))
        val event = publisher.events.single() as CartRefreshRequestedEvent
        kotlin.test.assertFailsWith<com.jstore.common.errors.BusinessErrorException> {
            CartRefreshRequestedHandler(service, carts).onDomainEvent(event)
        }
    }

    @Test
    fun `current assessment preserves each result and old assessment is stale`() {
        for (status in AssessmentStatus.entries) {
            val carts = InMemoryCartRepository()
            val assessments = InMemoryCartAssessmentStore()
            val publisher = RecordingPublisher()
            var nextId = 10L
            val service =
                CartApplicationService(
                    carts,
                    assessments,
                    FixedCommerceFacts(),
                    CartIdentityGenerator { nextId++ },
                    publisher,
                    limitsProvider = CartLimitsProvider { CartLimits(1, 999, 100) },
                )
            val first =
                assertIs<Success<CartView>>(
                        service.setItemQuantity(SetCartItemQuantityCommand(7, 101, 201, 3, 0))
                    )
                    .value
            assessments.save(
                CartAssessment(
                    CartAssessmentId(100),
                    CartId(first.cartId),
                    first.contentVersion,
                    status,
                    Price.ZERO,
                    "CNY",
                    Instant.EPOCH,
                    emptyList(),
                )
            )
            val current = assertIs<Success<CartView>>(service.current(7)).value
            assertEquals(status.name, current.assessment!!.status.toString())
            val changed =
                assertIs<Success<CartView>>(
                        service.replaceSelection(ReplaceCartSelectionCommand(7, 1, emptySet()))
                    )
                    .value
            assertEquals("STALE", changed.assessment!!.status.toString())
            assertEquals(1L, changed.assessment.sourceCartVersion)
            assertEquals(
                listOf("ITEM_QUANTITY_SET", "SELECTION_CHANGED"),
                publisher.events.map { (it as CartRefreshRequestedEvent).reason.toString() },
            )
        }
    }

    @Test
    fun `one quantity use case keeps its snapshot across offer lookup and next request sees refresh`() {
        var latest = CartLimits(1, 2000, 10)
        var reads = 0
        val facts =
            object : CartCommerceFactsService {
                override fun findOffer(offerId: OfferId): OfferIdentity {
                    latest = CartLimits(1, 5, 10)
                    return OfferIdentity(
                        offerId,
                        SkuId(101),
                        301,
                        SettlementScope("CN", "ONLINE", "CNY"),
                    )
                }

                override fun collect(lines: List<CartLine>) = emptyList<CartLineCommerceFacts>()
            }
        val carts = InMemoryCartRepository()
        val publisher = RecordingPublisher()
        var id = 10L
        val service =
            CartApplicationService(
                carts,
                InMemoryCartAssessmentStore(),
                facts,
                CartIdentityGenerator { id++ },
                publisher,
                limitsProvider =
                    CartLimitsProvider {
                        reads++
                        latest
                    },
            )
        assertIs<Success<CartView>>(
            service.setItemQuantity(SetCartItemQuantityCommand(7, 101, 201, 1500, 0))
        )
        assertEquals(1, reads)
        val rejected = service.setItemQuantity(SetCartItemQuantityCommand(7, 101, 201, 1500, 0))
        assertEquals(
            CartErrors.INVALID_QUANTITY,
            assertIs<com.jstore.common.utils.Failure<*>>(rejected).error,
        )
        assertEquals(2, reads)
        assertEquals(1, carts.saveCount)
        assertEquals(1, publisher.events.size)
    }

    private class InMemoryCartRepository : CartRepository {
        private var cart: Cart? = null
        var saveCount = 0
            private set

        override fun save(aggregate: Cart): Cart {
            cart = aggregate
            saveCount++
            return aggregate
        }

        override fun findById(id: CartId): Cart? = cart?.takeIf { it.id == id }

        override fun findActiveByBuyerId(buyerId: BuyerId): Cart? = cart?.takeIf {
            it.buyerId == buyerId && it.status == CartStatus.ACTIVE
        }
    }

    private class InMemoryCartAssessmentStore : CartAssessmentStore {
        private val values = mutableListOf<CartAssessment>()

        override fun save(assessment: CartAssessment): CartAssessment = assessment.also(values::add)

        override fun findById(id: CartAssessmentId): CartAssessment? = values.find { it.id == id }

        override fun findByCartAndVersion(cartId: CartId, version: Long): CartAssessment? =
            values.find {
                it.cartId == cartId && it.sourceCartVersion == version
            }

        override fun findLatestByCart(cartId: CartId): CartAssessment? =
            values.filter { it.cartId == cartId }.maxByOrNull { it.sourceCartVersion }
    }

    private class FixedCommerceFacts : CartCommerceFactsService {
        var available = true
        var failWhenCollecting = false
        var collectCalls = 0
            private set

        override fun findOffer(offerId: OfferId) =
            if (available) {
                OfferIdentity(
                    offerId = offerId,
                    skuId = SkuId(101),
                    merchantId = 301,
                    settlementScope = SettlementScope("CN", "ONLINE", "CNY"),
                )
            } else {
                null
            }

        override fun collect(lines: List<CartLine>): List<CartLineCommerceFacts> {
            collectCalls++
            check(!failWhenCollecting) { "assessment facts are unavailable" }
            return emptyList()
        }
    }

    private class RecordingPublisher : DomainEventPublisher {
        val events = mutableListOf<DomainEvent>()

        override fun publishEvent(event: DomainEvent) {
            events.add(event)
        }
    }
}
