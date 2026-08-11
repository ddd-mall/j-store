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
package com.jstore.order.service

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.acl.UserService
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderErrors
import com.jstore.order.domain.order.OrderFactory
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.OrderRepository
import com.jstore.order.domain.order.PaymentStatus
import com.jstore.order.domain.order.TradeStatus
import com.jstore.order.domain.order.UserInfo
import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.domain.order.event.OrderCompletedEvent
import com.jstore.order.domain.order.event.OrderStockConfirmedEvent
import com.jstore.order.domain.order.testOrder
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class OrderServiceStatusDimensionsTest :
    FunSpec({
        test("order factory failure is propagated without persistence or publication") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val publisher = mock(DomainEventPublisher::class.java)
            val users = mock(UserService::class.java)
            val command = validCreateCommand()
            val buyer = UserInfo(command.buyerUid, PhoneNumber("+8613800138000"), "buyer")
            val error = OrderErrors.CORRESPONDING_GOODS_NOT_FOUND
            `when`(users.findUserInfo(command.buyerUid)).thenReturn(buyer)
            `when`(factory.create(command, buyer)).thenReturn(Failure(error))

            OrderService(factory, repository, publisher, users).createOrder(command) shouldBe
                Failure(error)

            verifyNoInteractions(repository, publisher)
        }

        test("domain failure is propagated and aggregate is not saved") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val publisher = mock(DomainEventPublisher::class.java)
            val order = mock(Order::class.java)
            val id = OrderId(1)
            `when`(repository.findById(id)).thenReturn(order)
            `when`(order.confirmStock()).thenReturn(Failure(OrderErrors.ILLEGAL_STATE))

            OrderService(factory, repository, publisher, mock(UserService::class.java))
                .confirmStock(id)
                .shouldBeInstanceOf<Failure<*>>()
            verify(repository, never()).save(order)
        }

        test("stock confirmation persists order and publishes payment creation gate event") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val order =
                testOrder(
                    trade = TradeStatus.CREATED,
                    commitment = com.jstore.order.domain.order.CommitmentStatus.OFFER_AUTHORIZED,
                )
            val published = mutableListOf<com.jstore.common.framework.event.DomainEvent>()
            val publisher =
                object : DomainEventPublisher {
                    override fun publishEvent(
                        event: com.jstore.common.framework.event.DomainEvent
                    ) {
                        published += event
                    }
                }
            `when`(repository.findById(order.id)).thenReturn(order)
            `when`(repository.save(order)).thenReturn(order)

            OrderService(factory, repository, publisher, mock(UserService::class.java))
                .confirmStock(order.id) shouldBe Success(Unit)

            verify(repository).save(order)
            published.single().shouldBeInstanceOf<OrderStockConfirmedEvent>()
            order.pendingDomainEvents().size shouldBe 0
        }

        test("completing an order persists and publishes OrderCompletedEvent") {
            val factory = mock(OrderFactory::class.java)
            val repository = mock(OrderRepository::class.java)
            val order =
                testOrder(
                    trade = TradeStatus.ACTIVE,
                    payment = PaymentStatus.PAID,
                    fulfillment = FulfillmentStatus.DELIVERED,
                    itemStatuses = listOf(OrderItemStatus.SHIPPING_FINISHED),
                )
            val published = mutableListOf<com.jstore.common.framework.event.DomainEvent>()
            val publisher =
                object : DomainEventPublisher {
                    override fun publishEvent(
                        event: com.jstore.common.framework.event.DomainEvent
                    ) {
                        published += event
                    }
                }
            `when`(repository.findById(order.id)).thenReturn(order)
            `when`(repository.save(order)).thenReturn(order)

            OrderService(factory, repository, publisher, mock(UserService::class.java))
                .completeOrder(order.id) shouldBe Success(Unit)

            published.single().shouldBeInstanceOf<OrderCompletedEvent>()
            order.pendingDomainEvents().size shouldBe 0
        }
    })

private fun validCreateCommand() =
    OrderCreateCMD(
        buyerUid = 1L,
        merchantId = 7L,
        recipientInfo =
            OrderCreateCMD.RecipientInfoCMD(
                consigneeName = "recipient",
                consigneeContractInfo =
                    OrderCreateCMD.ContractInfoCMD(phoneNumber = PhoneNumber("+8613900139000")),
                countryCode = "CN",
                shippingDistrictCode = "110000",
                shippingDetailAddress = "detail address",
            ),
        items =
            listOf(
                OrderCreateCMD.OrderItemCMD(
                    spuId = 1L,
                    skuId = 1L,
                    quantity = 1,
                    snapshotVersion = 1L,
                )
            ),
    )
