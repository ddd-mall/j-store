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
import com.jstore.trade.domain.persistence.*
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Repository
class TradeProcessRepositoryImpl(private val jpa: TradeProcessPOJpaRepository) :
    TradeProcessRepository {
    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: TradeProcess): TradeProcess = toDomain(jpa.save(toPO(aggregate)))

    override fun findById(id: TradeProcessId): TradeProcess? =
        jpa.findById(id.value).orElse(null)?.let(::toDomain)

    private fun toPO(trade: TradeProcess) =
        TradeProcessPO(
            id = trade.id.value,
            orderId = trade.orderId,
            merchantId = trade.merchantId,
            payableAmountFen = trade.payableAmount.fen,
            currency = trade.currency,
            status = trade.status,
            items =
                trade.items
                    .map {
                        TradeItemPO(
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
                    }
                    .toMutableList(),
            authorizations =
                trade.authorizations
                    .map { TradeAuthorizationPO(it.authorizationId, it.offerId, it.expiresAt) }
                    .toMutableList(),
            reservations = trade.reservationIds.map(::TradeReservationPO).toMutableList(),
            reservationExpiresAt = trade.reservationExpiresAt,
            failureReason = trade.failureReason,
            closeReason = trade.closeReason,
            createdAt = trade.createdAt,
            updatedAt = trade.updatedAt,
            persistenceVersion = trade.persistenceVersion,
        )

    private fun toDomain(po: TradeProcessPO) =
        TradeProcess(
            id = TradeProcessId(po.id),
            orderId = po.orderId,
            merchantId = po.merchantId,
            items =
                po.items.map {
                    TradeItemSnapshot(
                        it.offerId,
                        it.storeId,
                        it.spuId,
                        it.skuId,
                        it.quantity,
                        it.catalogSnapshotVersion,
                        it.offerVersion,
                        it.fulfillmentNodeId,
                        it.channelId,
                        Price.ofFen(it.unitPriceFen),
                    )
                },
            payableAmount = Price.ofFen(po.payableAmountFen),
            currency = po.currency,
            status = po.status,
            authorizations =
                po.authorizations.map {
                    TradeAuthorization(it.authorizationId, it.offerId, it.expiresAt)
                },
            reservationIds = po.reservations.map { it.reservationId },
            reservationExpiresAt = po.reservationExpiresAt,
            failureReason = po.failureReason,
            closeReason = po.closeReason,
            createdAt = po.createdAt,
            updatedAt = po.updatedAt,
            persistenceVersion = po.persistenceVersion,
        )
}
