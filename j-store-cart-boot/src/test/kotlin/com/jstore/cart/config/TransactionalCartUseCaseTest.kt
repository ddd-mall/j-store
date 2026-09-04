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
package com.jstore.cart.config

import com.jstore.cart.acl.OfferIdentity
import com.jstore.cart.api.CartCheckoutSourceQuery
import com.jstore.cart.api.CartCheckoutSourceResult
import com.jstore.cart.domain.BuyerId
import com.jstore.cart.domain.Cart
import com.jstore.cart.domain.CartId
import com.jstore.cart.domain.CartLineCommerceFacts
import com.jstore.cart.domain.OfferId
import com.jstore.cart.domain.SettlementScope
import com.jstore.cart.domain.SkuId
import com.jstore.cart.service.*
import com.jstore.common.utils.Success
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus

class TransactionalCartUseCaseTest {
    @Test
    fun `resolves offer outside transaction and commits cart in a short write transaction`() {
        val transactions = RecordingCartTransactions()
        val delegate = mock<CartApplicationService>()
        val command = command()
        val offer = offer()
        whenever(delegate.inspectSetItemQuantity(command))
            .thenReturn(Success(SetCartItemQuantityStart.RequiresOffer))
        whenever(delegate.resolveOffer(command.offerId)).thenAnswer {
            assertFalse(transactions.inTransaction)
            offer
        }
        whenever(delegate.commitSetItemQuantity(command, offer)).thenAnswer {
            assertTrue(transactions.inWriteTransaction)
            Success(view())
        }

        val result = TransactionalCartUseCase(delegate, transactions).setItemQuantity(command)

        assertInstanceOf(Success::class.java, result)
        assertEquals(listOf("read", "external", "write"), transactions.phases)
    }

    @Test
    fun `optimistic conflict retries only the short write phase`() {
        val transactions = RecordingCartTransactions()
        val delegate = mock<CartApplicationService>()
        val command = command()
        val offer = offer()
        whenever(delegate.inspectSetItemQuantity(command))
            .thenReturn(Success(SetCartItemQuantityStart.RequiresOffer))
        whenever(delegate.resolveOffer(command.offerId)).thenReturn(offer)
        whenever(delegate.commitSetItemQuantity(command, offer))
            .thenThrow(OptimisticLockingFailureException("concurrent cart update"))
            .thenReturn(Success(view()))

        val result = TransactionalCartUseCase(delegate, transactions).setItemQuantity(command)

        assertInstanceOf(Success::class.java, result)
        assertEquals(listOf("read", "external", "write", "write"), transactions.phases)
    }

    @Test
    fun `collects refresh facts outside transaction and saves assessment in a short transaction`() {
        val transactions = RecordingCartTransactions()
        val delegate = mock<CartApplicationService>()
        val cart = cart()
        whenever(delegate.startRefresh(7, 0))
            .thenReturn(Success(CartRefreshStart.RequiresFacts(cart)))
        whenever(delegate.collectFacts(cart)).thenAnswer {
            assertFalse(transactions.inTransaction)
            Success(emptyList<CartLineCommerceFacts>())
        }
        whenever(delegate.completeRefresh(cart, emptyList<CartLineCommerceFacts>())).thenAnswer {
            assertTrue(transactions.inWriteTransaction)
            Success(view())
        }

        val result = TransactionalCartUseCase(delegate, transactions).refresh(7, 0)

        assertInstanceOf(Success::class.java, result)
        assertEquals(listOf("read", "external", "write"), transactions.phases)
    }

    @Test
    fun `prepares checkout facts outside the cart read transaction`() {
        val transactions = RecordingCartTransactions()
        val delegate = mock<CartApplicationService>()
        val cart = cart()
        val query = CartCheckoutSourceQuery(cartId = 1, buyerId = 7, expectedCartVersion = 0)
        whenever(delegate.startPrepare(query))
            .thenReturn(CartCheckoutPreparationStart.RequiresFacts(cart))
        whenever(delegate.prepareWithFacts(cart)).thenAnswer {
            assertFalse(transactions.inTransaction)
            CartCheckoutSourceResult.NoEligibleLines
        }

        val result =
            TransactionalCartCheckoutSourceQueryService(delegate, transactions).prepare(query)

        assertEquals(CartCheckoutSourceResult.NoEligibleLines, result)
        assertEquals(listOf("read", "external"), transactions.phases)
    }

    @Test
    fun `spring transaction operations use isolated transactions and suspend external calls`() {
        val manager = mock<PlatformTransactionManager>()
        val status = mock<TransactionStatus>()
        whenever(manager.getTransaction(any())).thenReturn(status)
        val operations = SpringCartTransactionOperations(manager)

        operations.read { "read" }
        operations.withoutTransaction { "external" }
        operations.write { "write" }

        val definitions = argumentCaptor<TransactionDefinition>()
        verify(manager, times(3)).getTransaction(definitions.capture())
        assertEquals(
            listOf(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED,
                TransactionDefinition.PROPAGATION_REQUIRES_NEW,
            ),
            definitions.allValues.map { it.propagationBehavior },
        )
        assertTrue(definitions.firstValue.isReadOnly)
    }

    private fun command() =
        SetCartItemQuantityCommand(
            buyerId = 7,
            skuId = 101,
            offerId = 201,
            targetQuantity = 3,
            expectedCartVersion = 12,
        )

    private fun offer() =
        OfferIdentity(
            offerId = OfferId(201),
            skuId = SkuId(101),
            merchantId = 301,
            settlementScope = SettlementScope("CN", "ONLINE", "CNY"),
        )

    private fun cart() =
        Cart.create(
            id = CartId(1),
            buyerId = BuyerId(7),
            scope = SettlementScope("CN", "ONLINE", "CNY"),
        )

    private fun view() =
        CartView(
            cartId = 1,
            contentVersion = 1,
            market = "CN",
            channelId = "ONLINE",
            currency = "CNY",
            lines = emptyList(),
            assessment = null,
        )
}

private class RecordingCartTransactions : CartTransactionOperations {
    val phases = mutableListOf<String>()
    var inTransaction = false
        private set

    var inWriteTransaction = false
        private set

    override fun <T : Any> read(action: () -> T): T = inTransaction("read", false, action)

    override fun <T : Any> write(action: () -> T): T = inTransaction("write", true, action)

    override fun <T : Any> withoutTransaction(action: () -> T): T {
        phases += "external"
        check(!inTransaction)
        return action()
    }

    private fun <T : Any> inTransaction(
        phase: String,
        write: Boolean,
        action: () -> T,
    ): T {
        phases += phase
        check(!inTransaction)
        inTransaction = true
        inWriteTransaction = write
        return try {
            action()
        } finally {
            inWriteTransaction = false
            inTransaction = false
        }
    }
}
