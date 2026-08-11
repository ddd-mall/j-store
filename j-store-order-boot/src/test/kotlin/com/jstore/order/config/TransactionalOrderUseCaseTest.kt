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
package com.jstore.order.config

import com.jstore.order.domain.order.command.OrderCreateCMD
import com.jstore.order.service.OrderUseCase
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.transaction.support.TransactionSynchronizationManager

class TransactionalOrderUseCaseTest {
    @Test
    fun `outbox failure rolls back business and outbox rows in the same transaction`() {
        EmbeddedPostgres.start().use { postgres ->
            val dataSource = postgres.postgresDatabase
            val jdbc = JdbcTemplate(dataSource)
            jdbc.execute("create table business_write(id bigint primary key)")
            jdbc.execute("create table outbox_write(id bigint primary key)")
            val delegate = mock<OrderUseCase>()
            doAnswer {
                    assertTrue(TransactionSynchronizationManager.isActualTransactionActive())
                    jdbc.update("insert into business_write(id) values (1)")
                    jdbc.update("insert into outbox_write(id) values (1)")
                    throw IllegalStateException("outbox serialization failed")
                }
                .`when`(delegate)
                .createOrder(any())
            val useCase =
                TransactionalOrderUseCase(
                    delegate,
                    DataSourceTransactionManager(dataSource),
                )

            assertFailsWith<IllegalStateException> {
                useCase.createOrder(mock<OrderCreateCMD>())
            }

            assertEquals(
                0,
                jdbc.queryForObject("select count(*) from business_write", Int::class.java),
            )
            assertEquals(
                0,
                jdbc.queryForObject("select count(*) from outbox_write", Int::class.java),
            )
        }
    }
}
