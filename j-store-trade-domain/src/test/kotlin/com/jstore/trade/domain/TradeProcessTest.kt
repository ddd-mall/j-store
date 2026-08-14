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
package com.jstore.trade.domain

import com.jstore.common.properties.Price
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TradeProcessTest {
    private val expiresAt = Instant.parse("2026-08-14T12:00:00Z")

    @Test
    fun `sale authorization advances trade from authorizing to reserving exactly once`() {
        val trade = trade()
        val authorizations = listOf(TradeAuthorization("auth-1", 10, expiresAt))

        assertEquals(
            true,
            assertIs<Success<Boolean>>(trade.recordSaleAuthorized(authorizations)).value,
        )
        assertEquals(TradeProcessStatus.RESERVING, trade.status)
        assertEquals(authorizations, trade.authorizations)
        assertEquals(
            false,
            assertIs<Success<Boolean>>(trade.recordSaleAuthorized(authorizations)).value,
        )
    }

    @Test
    fun `authorization must cover every offer and conflicting retry is rejected atomically`() {
        val trade = trade()

        assertIs<Failure<*>>(
            trade.recordSaleAuthorized(listOf(TradeAuthorization("auth-1", 99, expiresAt)))
        )
        assertEquals(TradeProcessStatus.AUTHORIZING, trade.status)
        assertEquals(emptyList(), trade.authorizations)
    }

    @Test
    fun `inventory reservation commits trade and persists earliest expiry`() {
        val trade = authorizedTrade()

        assertEquals(
            true,
            assertIs<Success<Boolean>>(
                    trade.recordInventoryReserved(
                        listOf("reservation-1"),
                        expiresAt.minusSeconds(10),
                    )
                )
                .value,
        )
        assertEquals(TradeProcessStatus.COMMITTED, trade.status)
        assertEquals(listOf("reservation-1"), trade.reservationIds)
        assertEquals(expiresAt.minusSeconds(10), trade.reservationExpiresAt)
    }

    @Test
    fun `failed or closed trade cannot be reopened by late success`() {
        val failed = trade()
        assertEquals(true, assertIs<Success<Boolean>>(failed.fail("offer rejected")).value)
        assertEquals(TradeProcessStatus.FAILED, failed.status)
        assertIs<Failure<*>>(
            failed.recordSaleAuthorized(listOf(TradeAuthorization("auth-1", 10, expiresAt)))
        )

        val closed = authorizedTrade()
        assertEquals(true, assertIs<Success<Boolean>>(closed.close("buyer cancelled")).value)
        assertEquals(TradeProcessStatus.CLOSED, closed.status)
        assertIs<Failure<*>>(closed.recordInventoryReserved(listOf("reservation-1"), expiresAt))
    }

    @Test
    fun `committed trade records paid fact idempotently`() {
        val trade = authorizedTrade()
        assertIs<Success<Boolean>>(
            trade.recordInventoryReserved(listOf("reservation-1"), expiresAt)
        )

        assertEquals(true, assertIs<Success<Boolean>>(trade.markPaid()).value)
        assertEquals(TradeProcessStatus.PAID, trade.status)
        assertEquals(false, assertIs<Success<Boolean>>(trade.markPaid()).value)
    }

    private fun authorizedTrade() =
        trade().also {
            assertIs<Success<Boolean>>(
                it.recordSaleAuthorized(listOf(TradeAuthorization("auth-1", 10, expiresAt)))
            )
        }

    private fun trade() =
        TradeProcess.start(
            id = TradeProcessId(100),
            orderId = 100,
            merchantId = 7,
            items =
                listOf(
                    TradeItemSnapshot(
                        offerId = 10,
                        storeId = 3,
                        spuId = 20,
                        skuId = 21,
                        quantity = 2,
                        catalogSnapshotVersion = 4,
                        offerVersion = 5,
                        fulfillmentNodeId = "CN-NORTH-1",
                        channelId = "ONLINE",
                        unitPrice = Price.ofFen(990),
                    )
                ),
            payableAmount = Price.ofFen(1980),
            currency = "CNY",
        )
}
