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
package com.jstore.trade.config

import com.jstore.common.utils.Success
import com.jstore.trade.service.CheckoutAccepted
import com.jstore.trade.service.CheckoutApplicationService
import com.jstore.trade.service.CheckoutItem
import com.jstore.trade.service.CheckoutRecipient
import com.jstore.trade.service.CreateCheckoutCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.support.SimpleTransactionStatus

class TransactionalCheckoutUseCaseTest {
    @Test
    fun `unique-key race recovers the winning checkout in a fresh transaction`() {
        val delegate = mock(CheckoutApplicationService::class.java)
        val transactions = RecordingTransactionManager()
        val command = command()
        `when`(delegate.checkout(command)).thenThrow(DataIntegrityViolationException("duplicate"))
        `when`(delegate.recoverConcurrentCheckout(command))
            .thenReturn(Success(CheckoutAccepted(9001, emptyList())))

        val result = TransactionalCheckoutUseCase(delegate, transactions).checkout(command)

        assertEquals(9001, assertIs<Success<CheckoutAccepted>>(result).value.tradeId)
        assertEquals(listOf(false, true), transactions.readOnlyTransactions)
        verify(delegate).recoverConcurrentCheckout(command)
    }

    private fun command() =
        CreateCheckoutCommand(
            "checkout-1",
            8,
            CheckoutRecipient(
                "buyer",
                "CN",
                "13800000000",
                null,
                "110101",
                "No. 1 Road",
            ),
            listOf(CheckoutItem(11, 1, 21, 31, 1, 1)),
        )
}

private class RecordingTransactionManager : PlatformTransactionManager {
    val readOnlyTransactions = mutableListOf<Boolean>()

    override fun getTransaction(definition: TransactionDefinition?): TransactionStatus {
        readOnlyTransactions += definition?.isReadOnly == true
        return SimpleTransactionStatus()
    }

    override fun commit(status: TransactionStatus) = Unit

    override fun rollback(status: TransactionStatus) = Unit
}
