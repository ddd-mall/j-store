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

import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.order.domain.aftersale.AfterSale
import com.jstore.order.domain.aftersale.AfterSaleErrors
import com.jstore.order.domain.aftersale.AfterSaleId
import com.jstore.order.domain.aftersale.MerchantActorId
import com.jstore.order.domain.order.OrderId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.Mockito.mock
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.`when`

class AfterSaleAccessServiceTest {
    private val afterSales = mock(AfterSaleUseCase::class.java)
    private val aggregate = mock(AfterSale::class.java)

    @Test
    fun `same numeric buyer in another authentication domain cannot read after sale`() {
        val id = AfterSaleId(8)
        val orderId = OrderId(9)
        `when`(aggregate.orderId).thenReturn(orderId)
        `when`(afterSales.findById(id)).thenReturn(Success(aggregate))
        `when`(afterSales.listByOrderForAccess(orderId))
            .thenReturn(
                Success(
                    AfterSaleOrderAccess(
                        buyerAuthenticationDomain = "issuer-a",
                        buyerId = 3,
                        merchantId = MerchantActorId(7),
                        afterSales = listOf(aggregate),
                    )
                )
            )
        val authorization = mock(com.jstore.shop.api.MerchantAuthorizationQuery::class.java)
        val service = AfterSaleAccessService(afterSales, authorization)

        val result = service.get("issuer-b", accountId = 3, id)

        assertEquals(AfterSaleErrors.NOT_FOUND, assertIs<Failure<*>>(result).error)
    }

    @Test
    fun `buyer access is scoped by both authentication domain and account id`() {
        val orderId = OrderId(9)
        `when`(afterSales.listByOrderForAccess(orderId))
            .thenReturn(
                Success(
                    AfterSaleOrderAccess(
                        buyerAuthenticationDomain = "issuer-a",
                        buyerId = 3,
                        merchantId = MerchantActorId(7),
                        afterSales = listOf(aggregate),
                    )
                )
            )
        val authorization = mock(com.jstore.shop.api.MerchantAuthorizationQuery::class.java)
        val service = AfterSaleAccessService(afterSales, authorization)

        val result = service.list("issuer-a", accountId = 3, orderId)

        assertEquals(listOf(aggregate), assertIs<Success<List<AfterSale>>>(result).value)
        verifyNoInteractions(authorization)
    }
}
