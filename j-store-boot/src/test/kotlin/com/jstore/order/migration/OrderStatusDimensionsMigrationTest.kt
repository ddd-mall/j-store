package com.jstore.order.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderStatusDimensionsMigrationTest {
    private lateinit var postgres: EmbeddedPostgres
    private lateinit var jdbcUrl: String

    @BeforeAll
    fun migrateCurrentSchema() {
        postgres = EmbeddedPostgres.builder().start()
        jdbcUrl = postgres.getJdbcUrl("postgres", "postgres")
        Flyway.configure()
            .dataSource(jdbcUrl, "postgres", null)
            .defaultSchema("develop")
            .schemas("develop")
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }

    @AfterAll
    fun stopPostgres() {
        if (::postgres.isInitialized) postgres.close()
    }

    @Test
    fun `migration replaces legacy order status structure with four constrained dimensions`() {
        connection().use { connection ->
            val columns = orderColumns(connection)

            assertStatusColumn(columns, "trade_status", "CREATED")
            assertStatusColumn(columns, "payment_status", "UNPAID")
            assertStatusColumn(columns, "fulfillment_status", "UNFULFILLED")
            assertStatusColumn(columns, "after_sale_status", "NONE")
            assertFalse("status" in columns)
            assertFalse("previous_status" in columns)

            val constraints = orderCheckConstraints(connection)
            assertCheckValues(constraints, "chk_orders_trade_status", "CREATED", "ACTIVE", "CLOSED", "COMPLETED")
            assertCheckValues(constraints, "chk_orders_payment_status", "UNPAID", "PAID", "PARTIALLY_REFUNDED", "REFUNDED")
            assertCheckValues(constraints, "chk_orders_fulfillment_status", "UNFULFILLED", "PENDING_SHIPMENT", "SHIPPED", "DELIVERED")
            assertCheckValues(constraints, "chk_orders_after_sale_status", "NONE", "PROCESSING", "PARTIALLY_COMPLETED", "COMPLETED")

            val indexes = orderIndexes(connection)
            assertStatusTimeIndex(indexes, "idx_orders_trade_status_create_time", "trade_status")
            assertStatusTimeIndex(indexes, "idx_orders_payment_status_create_time", "payment_status")
            assertStatusTimeIndex(indexes, "idx_orders_fulfillment_status_create_time", "fulfillment_status")
            assertStatusTimeIndex(indexes, "idx_orders_after_sale_status_create_time", "after_sale_status")
            assertFalse("idx_orders_status_create_time" in indexes)
        }
    }

    private fun connection(): Connection =
        DriverManager.getConnection(jdbcUrl, "postgres", null)

    private fun orderColumns(connection: Connection): Map<String, ColumnMetadata> =
        connection.prepareStatement(
            """
            SELECT column_name, data_type, character_maximum_length, is_nullable, column_default
            FROM information_schema.columns
            WHERE table_schema = 'develop' AND table_name = 'orders'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getString("column_name"),
                            ColumnMetadata(
                                dataType = rows.getString("data_type"),
                                maximumLength = rows.getInt("character_maximum_length"),
                                nullable = rows.getString("is_nullable"),
                                defaultExpression = rows.getString("column_default"),
                            ),
                        )
                    }
                }
            }
        }

    private fun orderCheckConstraints(connection: Connection): Map<String, String> =
        connection.prepareStatement(
            """
            SELECT constraint_name, check_clause
            FROM information_schema.check_constraints
            WHERE constraint_schema = 'develop' AND constraint_name LIKE 'chk_orders_%_status'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) put(rows.getString("constraint_name"), rows.getString("check_clause"))
                }
            }
        }

    private fun orderIndexes(connection: Connection): Map<String, String> =
        connection.prepareStatement(
            """
            SELECT indexname, indexdef
            FROM pg_indexes
            WHERE schemaname = 'develop' AND tablename = 'orders'
            """.trimIndent(),
        ).use { statement ->
            statement.executeQuery().use { rows ->
                buildMap {
                    while (rows.next()) put(rows.getString("indexname"), rows.getString("indexdef"))
                }
            }
        }

    private fun assertStatusColumn(columns: Map<String, ColumnMetadata>, name: String, defaultValue: String) {
        val column = requireNotNull(columns[name]) { "Missing orders.$name" }
        assertEquals("character varying", column.dataType)
        assertEquals(32, column.maximumLength)
        assertEquals("NO", column.nullable)
        assertEquals(defaultValue, quotedValues(requireNotNull(column.defaultExpression)).single())
    }

    private fun assertCheckValues(constraints: Map<String, String>, name: String, vararg expectedValues: String) {
        val definition = requireNotNull(constraints[name]) { "Missing constraint $name" }
        assertEquals(expectedValues.toSet(), quotedValues(definition), definition)
    }

    private fun quotedValues(expression: String): Set<String> =
        Regex("'([^']+)'").findAll(expression).map { it.groupValues[1] }.toSet()

    private fun assertStatusTimeIndex(indexes: Map<String, String>, name: String, statusColumn: String) {
        val definition = requireNotNull(indexes[name]) { "Missing index $name" }
        assertTrue(
            Regex("\\($statusColumn, create_time DESC\\)", RegexOption.IGNORE_CASE).containsMatchIn(definition),
            definition,
        )
    }

    private data class ColumnMetadata(
        val dataType: String,
        val maximumLength: Int,
        val nullable: String,
        val defaultExpression: String?,
    )
}
