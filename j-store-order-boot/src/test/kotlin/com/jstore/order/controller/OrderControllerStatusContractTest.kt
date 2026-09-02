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
package com.jstore.order.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jstore.common.properties.Price
import com.jstore.common.query.SortedPage
import com.jstore.common.utils.Success
import com.jstore.order.domain.order.CommitmentStatus
import com.jstore.order.domain.order.FulfillmentStatus
import com.jstore.order.domain.order.MerchantId
import com.jstore.order.domain.order.Order
import com.jstore.order.domain.order.OrderAmountSnapshot
import com.jstore.order.domain.order.OrderId
import com.jstore.order.domain.order.OrderItem
import com.jstore.order.domain.order.OrderItemId
import com.jstore.order.domain.order.OrderItemStatus
import com.jstore.order.domain.order.PaymentStatus
import com.jstore.order.domain.order.TradeStatus
import com.jstore.order.domain.order.UserInfo
import com.jstore.order.service.OrderUseCase
import com.jstore.user.domain.useraccount.UserId
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.bind.annotation.PostMapping

class OrderControllerStatusContractTest {
    @Test
    fun `order public boundary does not expose a creation endpoint`() {
        val publicPosts =
            OrderController::class.java.declaredMethods.filter {
                it.getAnnotation(PostMapping::class.java) != null
            }

        assertFalse(publicPosts.any { it.name == "createOrder" })
        assertFalse(OrderUseCase::class.java.methods.any { it.name == "createOrder" })
    }

    @Test
    fun `single order response exposes three dimensions and refund summary`() {
        val service = mock(OrderUseCase::class.java)
        val order = mock(Order::class.java)
        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        `when`(order.id).thenReturn(OrderId(1))
        `when`(order.merchantId).thenReturn(MerchantId(7))
        `when`(order.buyerInfo).thenReturn(UserInfo(2, null, null))
        `when`(order.tradeStatus).thenReturn(TradeStatus.ACTIVE)
        `when`(order.paymentStatus).thenReturn(PaymentStatus.PARTIALLY_REFUNDED)
        `when`(order.fulfillmentStatus).thenReturn(FulfillmentStatus.DELIVERED)
        `when`(order.commitmentStatus).thenReturn(CommitmentStatus.CONFIRMED)
        `when`(order.amountSnapshot)
            .thenReturn(OrderAmountSnapshot.singleCurrency("CNY", Price.ofFen(100)))
        `when`(order.paidAmount).thenReturn(Price.ofFen(100))
        `when`(order.refundedAmount).thenReturn(Price.ofFen(50))
        val item = mock(OrderItem::class.java)
        `when`(item.id).thenReturn(OrderItemId(10))
        `when`(item.skuId).thenReturn(11)
        `when`(item.spuId).thenReturn(12)
        `when`(item.offerId).thenReturn(13)
        `when`(item.storeId).thenReturn(14)
        `when`(item.offerVersion).thenReturn(2)
        `when`(item.fulfillmentNodeId).thenReturn("CN-NORTH-1")
        `when`(item.channelId).thenReturn("ONLINE")
        `when`(item.goodsName).thenReturn("商品")
        `when`(item.skuDescription).thenReturn("规格")
        `when`(item.quantity).thenReturn(1)
        `when`(item.unitPrice).thenReturn(Price.ofFen(100))
        `when`(item.status).thenReturn(OrderItemStatus.CANCELED)
        `when`(item.refundedQuantity).thenReturn(1)
        `when`(item.refundedAmount).thenReturn(Price.ofFen(50))
        `when`(order.items).thenReturn(listOf(item))
        `when`(order.createTime).thenReturn(now)
        `when`(order.updateTime).thenReturn(now)
        `when`(service.getOrderById(2, OrderId(1))).thenReturn(Success(order))

        val body = OrderController(service).getOrder(UserId(2), 1).body
        val json =
            jacksonObjectMapper()
                .findAndRegisterModules()
                .valueToTree<com.fasterxml.jackson.databind.JsonNode>(body)
        assertEquals("ACTIVE", json["tradeStatus"].asText())
        assertEquals("PARTIALLY_REFUNDED", json["paymentStatus"].asText())
        assertEquals("DELIVERED", json["fulfillmentStatus"].asText())
        assertEquals("CONFIRMED", json["commitmentStatus"].asText())
        assertFalse(json.has("afterSaleStatus"))
        assertEquals(50, json["refundedAmount"].asLong())
        assertEquals(100, json["payableAmount"].asLong())
        assertFalse(json.has("status"))
        assertFalse(json.has("buyerPhone"))
        assertFalse(json.has("buyerName"))
        assertEquals("CANCELED", json["items"][0]["status"].asText())

        `when`(service.pageListByUserId(2, 1, 10)).thenReturn(SortedPage(1, 1, listOf(order)))
        val pageJson =
            jacksonObjectMapper()
                .findAndRegisterModules()
                .valueToTree<com.fasterxml.jackson.databind.JsonNode>(
                    OrderController(service).listMyOrders(UserId(2), 1, 10).body
                )
        assertTrue(pageJson["records"][0].has("tradeStatus"))
        assertFalse(pageJson["records"][0].has("status"))
    }
}
