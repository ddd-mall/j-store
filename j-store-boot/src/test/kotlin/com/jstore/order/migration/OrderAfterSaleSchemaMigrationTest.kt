package com.jstore.order.migration
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils
import kotlin.test.*
class OrderAfterSaleSchemaMigrationTest{
 @Test fun `migration creates independent after-sale schema and removes legacy columns`(){EmbeddedPostgres.builder().start().use{pg->pg.postgresDatabase.connection.use{c->
  listOf("db/migration/V20260507__baseline_j_store_boot_schema.sql","db/migration/V20260731__order_status_dimensions.sql","db/migration/V20260803__order_after_sale_aggregate.sql").forEach{ScriptUtils.executeSqlScript(c,ClassPathResource(it))}
  fun table(name:String)=c.metaData.getTables(null,null,name,null).use{it.next()}
  fun column(table:String,name:String)=c.metaData.getColumns(null,null,table,name).use{it.next()}
  listOf("after_sales","after_sale_items","after_sale_capacities","after_sale_command_receipts","order_refund_facts").forEach{assertTrue(table(it),it)}
  assertFalse(column("orders","after_sale_status"));assertFalse(column("order_items","previous_item_status"));assertTrue(column("orders","total_refunded_amount"));assertTrue(column("orders","version"));assertTrue(column("order_items","refunded_quantity"));assertTrue(column("order_items","refunded_amount"))
 }}}
}
