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

import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.contracts.commerce.ContractSaleItem
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.trade.domain.Trade
import com.jstore.trade.domain.TradeOrderPlan
import com.jstore.trade.service.CheckoutApplicationService
import com.jstore.trade.service.CheckoutUseCase
import com.jstore.trade.service.CreateCheckoutCommand
import com.jstore.trade.service.TradeAuthorizationGateway
import java.time.Instant
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

/** Maps Trade's plan-level authorization request to the current Store integration language. */
class TradeAuthorizationMessageGateway(private val publisher: IntegrationMessagePublisher) :
    TradeAuthorizationGateway {
    override fun requestAuthorization(trade: Trade, plan: TradeOrderPlan) {
        val now = Instant.now()
        publisher.publish(
            AuthorizeSaleCommand(
                tradeId = trade.id.value,
                orderPlanId = plan.id.value,
                merchantId = plan.merchantId,
                items =
                    plan.items.map {
                        ContractSaleItem(
                            it.offerId,
                            it.storeId,
                            it.spuId,
                            it.skuId,
                            it.quantity,
                            it.catalogSnapshotVersion,
                            it.offerVersion,
                            it.fulfillmentNodeId,
                            it.channelId,
                            it.unitPrice.fen,
                        )
                    },
                sourceMessageId = "${trade.checkoutRequestId}:${plan.id.value}",
                occurredAtValue = now,
            )
        )
    }
}

class TransactionalCheckoutUseCase(
    private val delegate: CheckoutApplicationService,
    transactionManager: PlatformTransactionManager,
) : CheckoutUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun checkout(command: CreateCheckoutCommand) =
        try {
            requireNotNull(write.execute { delegate.checkout(command) })
        } catch (failure: DataIntegrityViolationException) {
            read.execute { delegate.recoverConcurrentCheckout(command) } ?: throw failure
        }

    override fun find(buyerAuthenticationDomain: String, buyerId: Long, tradeId: Long) =
        requireNotNull(read.execute { delegate.find(buyerAuthenticationDomain, buyerId, tradeId) })
}
