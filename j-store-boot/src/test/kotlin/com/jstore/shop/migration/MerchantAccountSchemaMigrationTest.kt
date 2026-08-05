package com.jstore.shop.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils

class MerchantAccountSchemaMigrationTest {
    @Test
    fun `migration creates strict merchant membership schema without legacy backfill`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            postgres.postgresDatabase.connection.use { connection ->
                listOf(
                        "db/migration/V20260507__baseline_j_store_boot_schema.sql",
                        "db/migration/V20260731__order_status_dimensions.sql",
                        "db/migration/V20260803__order_after_sale_aggregate.sql",
                        "db/migration/V20260805__order_payment_fulfillment_boundaries.sql",
                    )
                    .forEach { ScriptUtils.executeSqlScript(connection, ClassPathResource(it)) }

                ScriptUtils.executeSqlScript(
                    connection,
                    ClassPathResource(
                        "db/migration/V20260806__unified_account_merchant_membership.sql"
                    ),
                )

                fun table(name: String) =
                    connection.metaData.getTables(null, "develop", name, null).use { it.next() }
                assertTrue(table("merchants"))
                assertTrue(table("merchant_memberships"))
                assertTrue(table("merchant_membership_roles"))

                connection.createStatement().use { statement ->
                    statement.executeQuery("select count(*) from develop.merchants").use {
                        it.next()
                        assertEquals(0, it.getInt(1))
                    }
                    statement.executeUpdate(
                        "insert into develop.merchants(id,name,status,create_time,update_time) values(41,'示例商户','ACTIVE',now(),now())"
                    )
                    statement.executeUpdate(
                        "insert into develop.merchant_memberships(id,merchant_id,user_id,status,create_time,update_time) values(51,41,101,'ACTIVE',now(),now())"
                    )
                    statement.executeUpdate(
                        "insert into develop.merchant_membership_roles(membership_id,role) values(51,'OWNER')"
                    )
                    statement.executeUpdate(
                        "insert into develop.spu(id,name,description,status,version,merchant_id) values(1,'商品','','DRAFT',1,41)"
                    )
                }

                assertFailsWith<SQLException> {
                    connection.createStatement().use {
                        it.executeUpdate(
                            "insert into develop.merchant_memberships(merchant_id,user_id,status,create_time,update_time) values(41,101,'ACTIVE',now(),now())"
                        )
                    }
                }

                assertFailsWith<SQLException> {
                    connection.createStatement().use {
                        it.executeUpdate(
                            "insert into develop.spu(id,name,description,status,version,merchant_id) values(2,'非法商品','','DRAFT',1,999)"
                        )
                    }
                }
            }
        }
    }
}
