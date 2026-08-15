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
package com.jstore.payment.config

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.contracts.commerce.CancelPaymentInstallmentCommand
import com.jstore.contracts.commerce.PreparePaymentInstallmentCommand
import com.jstore.payment.service.TradePaymentCancellationService
import com.jstore.payment.service.TradePaymentCancellationStart
import com.jstore.payment.service.TradePaymentCancellationUseCase
import com.jstore.payment.service.TradePaymentPreparationService
import com.jstore.payment.service.TradePaymentPreparationStart
import com.jstore.payment.service.TradePaymentPreparationUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

interface TradePaymentPreparationTransactionOperations {
    fun <T : Any> durable(action: () -> T): T

    fun <T : Any> withoutTransaction(action: () -> T): T
}

class SpringTradePaymentPreparationTransactionOperations(
    transactionManager: PlatformTransactionManager
) : TradePaymentPreparationTransactionOperations {
    private val durable =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }
    private val external =
        TransactionTemplate(transactionManager).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_NOT_SUPPORTED
        }

    override fun <T : Any> durable(action: () -> T): T =
        requireNotNull(durable.execute { action() })

    override fun <T : Any> withoutTransaction(action: () -> T): T =
        requireNotNull(external.execute { action() })
}

class TransactionalTradePaymentPreparationUseCase(
    private val delegate: TradePaymentPreparationService,
    private val transactions: TradePaymentPreparationTransactionOperations,
) : TradePaymentPreparationUseCase {
    override fun prepare(
        command: PreparePaymentInstallmentCommand
    ): Result<Boolean, BusinessError> {
        return when (val start = transactions.durable { delegate.start(command) }) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is TradePaymentPreparationStart.Completed -> Success(value.changed)
                    is TradePaymentPreparationStart.Pending -> {
                        val providerResult = transactions.withoutTransaction {
                            delegate.invokeProvider(value.request)
                        }
                        transactions.durable {
                            delegate.complete(command, value.request.paymentId, providerResult)
                        }
                    }
                }
        }
    }
}

class TransactionalTradePaymentCancellationUseCase(
    private val delegate: TradePaymentCancellationService,
    private val transactions: TradePaymentPreparationTransactionOperations,
) : TradePaymentCancellationUseCase {
    override fun cancel(command: CancelPaymentInstallmentCommand): Result<Boolean, BusinessError> {
        return when (val start = transactions.durable { delegate.start(command) }) {
            is Failure -> start
            is Success ->
                when (val value = start.value) {
                    is TradePaymentCancellationStart.Completed -> Success(value.changed)
                    is TradePaymentCancellationStart.Pending -> {
                        val providerResult = transactions.withoutTransaction {
                            delegate.invokeProvider(value.request)
                        }
                        transactions.durable {
                            delegate.complete(command, value.request, providerResult)
                        }
                    }
                }
        }
    }
}
