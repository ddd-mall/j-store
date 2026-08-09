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
package com.jstore.inventory.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.EncodedResource
import org.springframework.jdbc.datasource.init.ScriptUtils

class CommerceSellabilityMigrationTest {
    @Test
    fun `migration preserves legacy inventory quantities at the default node`() =
        database { connection ->
            createLegacyInventory(connection)
            connection.createStatement().use {
                it.executeUpdate("insert into develop.inventory values (101, 7.00, 3.00, 4)")
            }

            migrate(connection)

            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        """
                        select id, sku_id, fulfillment_node_id, on_hand, reserved,
                               source_version, persistence_version
                        from develop.inventory_stock_positions
                        where id = '101@DEFAULT'
                        """
                            .trimIndent()
                    )
                    .use { row ->
                        assertTrue(row.next())
                        assertEquals(101L, row.getLong("sku_id"))
                        assertEquals("DEFAULT", row.getString("fulfillment_node_id"))
                        assertEquals(10, row.getInt("on_hand"))
                        assertEquals(3, row.getInt("reserved"))
                        assertEquals(0L, row.getLong("source_version"))
                        assertEquals(4L, row.getLong("persistence_version"))
                        assertFalse(row.next())
                    }
            }
            assertFalse(tableExists(connection, "inventory"))
        }

    @Test
    fun `migration rejects lossy legacy inventory and retains its source row`() =
        database { connection ->
            createLegacyInventory(connection)
            connection.createStatement().use {
                it.executeUpdate("insert into develop.inventory values (102, 1.50, 0.00, 0)")
            }

            assertFails { migrate(connection) }

            assertTrue(tableExists(connection, "inventory"))
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from develop.inventory").use { row ->
                    row.next()
                    assertEquals(1, row.getInt(1))
                }
            }
        }

    @Test
    fun `migration refuses to drop a nonempty undocumented inventory table`() =
        database { connection ->
            connection.createStatement().use {
                it.execute("create table develop.goods_inventory(id bigint primary key)")
                it.executeUpdate("insert into develop.goods_inventory values (1)")
            }

            assertFails { migrate(connection) }

            assertTrue(tableExists(connection, "goods_inventory"))
            connection.createStatement().use { statement ->
                statement.executeQuery("select count(*) from develop.goods_inventory").use { row ->
                    row.next()
                    assertEquals(1, row.getInt(1))
                }
            }
        }

    private fun database(block: (Connection) -> Unit) {
        EmbeddedPostgres.builder().start().use { postgres ->
            postgres.postgresDatabase.connection.use { connection ->
                listOf(
                        "db/migration/V20260507__baseline_j_store_boot_schema.sql",
                        "db/migration/V20260731__order_status_dimensions.sql",
                        "db/migration/V20260803__order_after_sale_aggregate.sql",
                        "db/migration/V20260805__order_payment_fulfillment_boundaries.sql",
                        "db/migration/V20260806__unified_account_merchant_membership.sql",
                        "db/migration/V20260808__account_security_hardening.sql",
                    )
                    .forEach { ScriptUtils.executeSqlScript(connection, ClassPathResource(it)) }
                block(connection)
            }
        }
    }

    private fun createLegacyInventory(connection: Connection) {
        connection.createStatement().use {
            it.execute(
                """
                create table develop.inventory(
                    commodity_code bigint primary key,
                    available_quantity numeric(19,2) not null,
                    reserved_quantity numeric(19,2) not null,
                    version bigint not null
                )
                """
                    .trimIndent()
            )
        }
    }

    private fun migrate(connection: Connection) =
        ScriptUtils.executeSqlScript(
            connection,
            EncodedResource(
                ClassPathResource("db/migration/V20260809__commerce_sellability_boundaries.sql")
            ),
            false,
            false,
            ScriptUtils.DEFAULT_COMMENT_PREFIXES,
            ScriptUtils.EOF_STATEMENT_SEPARATOR,
            ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
            ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER,
        )

    private fun tableExists(connection: Connection, tableName: String): Boolean =
        connection.metaData.getTables(null, "develop", tableName, arrayOf("TABLE")).use {
            it.next()
        }
}
