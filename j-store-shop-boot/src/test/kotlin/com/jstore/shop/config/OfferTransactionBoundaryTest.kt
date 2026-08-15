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
package com.jstore.shop.config

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.contracts.commerce.AuthorizeSaleCommand
import com.jstore.shop.domain.offer.SaleAuthorization
import com.jstore.shop.domain.offer.SaleAuthorizationId
import com.jstore.shop.domain.offer.SaleAuthorizationRepository
import com.jstore.shop.domain.offer.SalesOfferGuard
import com.jstore.shop.domain.offer.StoreGuard
import com.jstore.shop.service.OfferAuthorizationService
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.AbstractPlatformTransactionManager
import org.springframework.transaction.support.DefaultTransactionStatus
import org.springframework.transaction.support.TransactionSynchronizationManager

class OfferTransactionBoundaryTest {
    @Test
    fun `authorize handler publishes its outcome inside a transaction`() {
        val transactionManager = RecordingTransactionManager()
        val publisher =
            object : DomainEventPublisher {
                override fun publishEvent(event: com.jstore.common.framework.event.DomainEvent) {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                }
            }
        val authorizations =
            object : SaleAuthorizationRepository {
                override fun save(aggregate: SaleAuthorization) = aggregate

                override fun findById(id: SaleAuthorizationId): SaleAuthorization? = null

                override fun findByOrderPlanId(orderPlanId: Long): List<SaleAuthorization> =
                    emptyList()
            }
        val service =
            OfferAuthorizationService(
                StoreGuard { emptyList() },
                SalesOfferGuard { emptyList() },
                authorizations,
                publisher,
            )
        val handler =
            OfferBootConfiguration().authorizeSaleHandler(service, publisher, transactionManager)

        handler.handle(AuthorizeSaleCommand(1, 11, 2, emptyList(), "source", Instant.EPOCH))

        assertEquals(1, transactionManager.commits)
    }

    private class RecordingTransactionManager : AbstractPlatformTransactionManager() {
        var commits = 0

        override fun doGetTransaction() = Any()

        override fun doBegin(transaction: Any, definition: TransactionDefinition) = Unit

        override fun doCommit(status: DefaultTransactionStatus) {
            commits++
        }

        override fun doRollback(status: DefaultTransactionStatus) = Unit
    }
}
