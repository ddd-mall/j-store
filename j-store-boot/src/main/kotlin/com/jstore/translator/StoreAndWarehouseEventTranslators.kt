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
package com.jstore.translator

import com.jstore.common.framework.event.DomainEventListener
import com.jstore.contracts.commerce.ContractAuthorizedSaleItem
import com.jstore.contracts.commerce.PhysicalStockChangedIntegrationEvent
import com.jstore.contracts.commerce.SaleAuthorizationFailedIntegrationEvent
import com.jstore.contracts.commerce.SaleAuthorizedIntegrationEvent
import com.jstore.messaging.IntegrationMessagePublisher
import com.jstore.shop.domain.offer.event.SaleAuthorizationRejectedEvent
import com.jstore.shop.domain.offer.event.SaleAuthorizedEvent
import com.jstore.warehouse.domain.event.PhysicalStockChangedEvent
import org.springframework.stereotype.Component

@Component
class SaleAuthorizedIntegrationTranslator(private val publisher: IntegrationMessagePublisher) :
    DomainEventListener<SaleAuthorizedEvent> {
    override fun listenerId() = "translator.store-sale-authorized.to-order.v1"

    override fun onDomainEvent(event: SaleAuthorizedEvent) {
        publisher.publish(
            SaleAuthorizedIntegrationEvent(
                tradeId = event.tradeId,
                orderPlanId = event.orderPlanId,
                items =
                    event.items.map {
                        ContractAuthorizedSaleItem(
                            it.authorizationId,
                            it.offerId,
                            it.skuId,
                            it.quantity,
                            it.fulfillmentNodeId,
                            it.expiresAt,
                        )
                    },
                sourceMessageId = event.eventId,
                occurredAtValue = event.occurredAt,
            )
        )
    }
}

@Component
class SaleAuthorizationRejectedIntegrationTranslator(
    private val publisher: IntegrationMessagePublisher
) : DomainEventListener<SaleAuthorizationRejectedEvent> {
    override fun listenerId() = "translator.store-sale-rejected.to-order.v1"

    override fun onDomainEvent(event: SaleAuthorizationRejectedEvent) {
        publisher.publish(
            SaleAuthorizationFailedIntegrationEvent(
                event.tradeId,
                event.orderPlanId,
                event.reason,
                event.eventId,
                event.occurredAt,
            )
        )
    }
}

@Component
class PhysicalStockChangedIntegrationTranslator(
    private val publisher: IntegrationMessagePublisher
) : DomainEventListener<PhysicalStockChangedEvent> {
    override fun listenerId() = "translator.warehouse-stock-changed.to-inventory.v1"

    override fun onDomainEvent(event: PhysicalStockChangedEvent) {
        publisher.publish(
            PhysicalStockChangedIntegrationEvent(
                event.skuId,
                event.fulfillmentNodeId,
                event.onHand,
                event.sourceVersion,
                event.reason,
                event.eventId,
                event.occurredAt,
            )
        )
    }
}
