package com.jstore.order.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils

class OrderAfterSaleSchemaMigrationTest {
    @Test
    fun `migration creates order payment fulfillment and staged after-sale schemas`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            postgres.postgresDatabase.connection.use { connection ->
                listOf(
                        "db/migration/V20260507__baseline_j_store_boot_schema.sql",
                        "db/migration/V20260731__order_status_dimensions.sql",
                        "db/migration/V20260803__order_after_sale_aggregate.sql",
                        "db/migration/V20260805__order_payment_fulfillment_boundaries.sql",
                    )
                    .forEach { ScriptUtils.executeSqlScript(connection, ClassPathResource(it)) }

                fun table(name: String) =
                    connection.metaData.getTables(null, null, name, null).use { it.next() }
                fun column(table: String, name: String) =
                    connection.metaData.getColumns(null, null, table, name).use { it.next() }

                listOf(
                        "after_sales",
                        "after_sale_items",
                        "after_sale_capacities",
                        "after_sale_command_receipts",
                        "order_refund_facts",
                        "payment_orders",
                        "payment_refunds",
                        "payment_refund_items",
                        "fulfillment_orders",
                        "fulfillment_items",
                    )
                    .forEach { assertTrue(table(it), it) }

                listOf(
                        "merchant_id",
                        "currency",
                        "items_subtotal",
                        "discount_amount",
                        "shipping_amount",
                        "tax_amount",
                        "payable_amount",
                        "paid_amount",
                        "refunded_amount",
                        "payment_reference",
                        "fulfillment_reference",
                    )
                    .forEach { assertTrue(column("orders", it), "orders.$it") }

                assertTrue(column("spu", "merchant_id"))
                assertTrue(column("spu_snapshot", "merchant_id"))
                assertTrue(column("after_sales", "return_received_at"))
                assertTrue(column("after_sales", "refund_id"))
                assertTrue(column("after_sales", "refund_failure_reason"))
                assertTrue(column("order_refund_facts", "refund_id"))
                assertFalse(column("orders", "total_amount"))
                assertFalse(column("orders", "actual_pay"))
                assertFalse(column("orders", "total_refunded_amount"))
            }
        }
    }
}
