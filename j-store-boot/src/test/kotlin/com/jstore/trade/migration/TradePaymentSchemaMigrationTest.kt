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
package com.jstore.trade.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.Test
import kotlin.test.assertEquals
import org.flywaydb.core.Flyway

class TradePaymentSchemaMigrationTest {
    @Test
    fun `payment provider fields belong to trade payments and not order plans`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            Flyway.configure()
                .dataSource(postgres.postgresDatabase)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .load()
                .migrate()

            postgres.postgresDatabase.connection.use { connection ->
                val providerColumns =
                    setOf(
                        "provider_reference",
                        "pay_action",
                        "provider_accepted_at",
                        "accept_before",
                        "expires_at",
                        "failure_reason",
                        "cancellation_reason",
                    )
                assertEquals(
                    providerColumns,
                    columns(connection, "trade_payments") intersect providerColumns,
                )
                assertEquals(
                    emptySet(),
                    columns(connection, "trade_order_plans") intersect providerColumns,
                )
                assertEquals(
                    setOf("trade_id", "installment_id", "payment_id"),
                    columns(connection, "trade_installment_payment_refs"),
                )
                assertEquals("text", columnType(connection, "trade_payments", "provider_reference"))
            }
        }
    }

    private fun columns(
        connection: java.sql.Connection,
        table: String,
    ): Set<String> =
        connection
            .prepareStatement(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_schema = 'develop' AND table_name = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setString(1, table)
                statement.executeQuery().use { result ->
                    buildSet {
                        while (result.next()) add(result.getString("column_name"))
                    }
                }
            }

    private fun columnType(
        connection: java.sql.Connection,
        table: String,
        column: String,
    ): String =
        connection
            .prepareStatement(
                """
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'develop' AND table_name = ? AND column_name = ?
                """
                    .trimIndent()
            )
            .use { statement ->
                statement.setString(1, table)
                statement.setString(2, column)
                statement.executeQuery().use { result ->
                    check(result.next()) { "Missing $table.$column" }
                    result.getString("data_type")
                }
            }
}
