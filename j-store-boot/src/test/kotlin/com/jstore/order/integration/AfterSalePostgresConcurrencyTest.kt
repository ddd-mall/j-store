package com.jstore.order.integration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AfterSalePostgresConcurrencyTest {
    @Test
    fun `capacity lock serializes concurrent reservations without overselling`() = database { pg ->
        seedCapacity(pg, 11, 1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val results = (1..2).map { pool.submit(Callable { reserve(pg, listOf(11), 1) }) }
                .map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(listOf(false, true), results.sorted())
            pg.connection.use { c -> assertEquals(1, queryInt(c, "select requested_quantity from after_sale_capacities where order_item_id=11")) }
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)) }
    }

    @Test
    fun `sorted multi-item locking completes in opposite request order without deadlock`() = database { pg ->
        seedCapacity(pg, 21, 2); seedCapacity(pg, 22, 2)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val a = pool.submit(Callable { reserve(pg, listOf(21, 22), 1) })
            val b = pool.submit(Callable { reserve(pg, listOf(22, 21), 1) })
            assertTrue(a.get(10, TimeUnit.SECONDS)); assertTrue(b.get(10, TimeUnit.SECONDS))
        } finally { pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS) }
    }

    @Test
    fun `optimistic decision and idempotency keys have one winner`() = database { pg ->
        pg.connection.use { c -> c.createStatement().use { it.executeUpdate("insert into after_sales(id,order_id,applicant_id,merchant_id,status,reason_category,reason_description,fulfillment_status,require_return,create_time,update_time,version) values(1,1,1,7,'REQUESTED','OTHER','x','UNFULFILLED',false,now(),now(),0)") } }
        val pool = Executors.newFixedThreadPool(2)
        try {
            val decisions = (1..2).map { pool.submit(Callable { updateDecision(pg) }) }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, decisions.count { it })
            val receipts = (1..2).map { i -> pool.submit(Callable { insertReceipt(pg, i.toLong()) }) }.map { it.get(10, TimeUnit.SECONDS) }
            assertEquals(1, receipts.count { it })
        } finally { pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS) }
    }

    @Test
    fun `receipt and outbox are rolled back together`() = database { pg ->
        pg.connection.use { c ->
            c.autoCommit = false
            c.createStatement().use {
                it.executeUpdate("insert into after_sale_command_receipts values(99,1,'CREATE','rollback','hash',1,'REQUESTED',now())")
                it.executeUpdate("insert into outbox_entry(id,event_id,event_type,event_class_name,event_version,payload,aggregate_type,aggregate_id,occurred_at,status,retry_count,next_attempt_at,created_at,updated_at) values('00000000-0000-0000-0000-000000000099','event-99','after-sale.requested','AfterSaleRequestedEvent',1,'{}','AfterSale','1',now(),'PENDING',0,now(),now(),now())")
            }
            c.rollback()
        }
        pg.connection.use { c ->
            assertEquals(0, queryInt(c, "select count(*) from after_sale_command_receipts where id=99"))
            assertEquals(0, queryInt(c, "select count(*) from outbox_entry where event_id='event-99'"))
        }
    }

    private fun database(block: (javax.sql.DataSource) -> Unit) {
        EmbeddedPostgres.builder().start().use { pg ->
            val dataSource = org.postgresql.ds.PGSimpleDataSource().apply {
                setURL(pg.getJdbcUrl("postgres", "postgres"))
                user = "postgres"
            }
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement -> statement.execute(
                    """create table after_sales(id bigint primary key,order_id bigint,applicant_id bigint,merchant_id bigint,status varchar(16),reason_category varchar(32),reason_description varchar(500),fulfillment_status varchar(32),require_return boolean,create_time timestamp,update_time timestamp,version bigint not null default 0);
                    create table after_sale_capacities(order_item_id bigint primary key,order_id bigint,quantity_ceiling integer,amount_ceiling numeric(19,0),requested_quantity integer default 0,requested_amount numeric(19,0) default 0,approved_quantity integer default 0,approved_amount numeric(19,0) default 0,version bigint default 0,check(requested_quantity+approved_quantity<=quantity_ceiling));
                    create table after_sale_command_receipts(id bigint primary key,actor_id bigint,command_type varchar(16),idempotency_key varchar(128),request_hash varchar(64),after_sale_id bigint,result_status varchar(16),created_at timestamp,unique(actor_id,command_type,idempotency_key));
                    create table outbox_entry(id varchar(36) primary key,event_type varchar(512),event_id varchar(64),event_class_name varchar(512),event_version integer,payload text,aggregate_type varchar(256),aggregate_id varchar(128),occurred_at timestamptz,status varchar(20),created_at timestamptz,updated_at timestamptz,retry_count integer,next_attempt_at timestamptz);"""
                ) }
            }
            block(dataSource)
        }
    }
    private fun seedCapacity(ds: javax.sql.DataSource, id: Long, ceiling: Int) = ds.connection.use { c -> c.createStatement().use { it.executeUpdate("insert into after_sale_capacities(order_item_id,order_id,quantity_ceiling,amount_ceiling,requested_quantity,requested_amount,approved_quantity,approved_amount,version) values($id,1,$ceiling,100,0,0,0,0,0)") } }
    private fun reserve(ds: javax.sql.DataSource, requestedIds: List<Long>, quantity: Int): Boolean = ds.connection.use { c ->
        c.autoCommit = false
        try {
            val ids = requestedIds.sorted()
            c.prepareStatement("select order_item_id from after_sale_capacities where order_item_id in (${ids.joinToString()}) order by order_item_id for update").use { it.executeQuery().use { rs -> while (rs.next()) Unit } }
            val available = ids.all { queryInt(c, "select quantity_ceiling-requested_quantity-approved_quantity from after_sale_capacities where order_item_id=$it") >= quantity }
            if (available) ids.forEach { c.createStatement().use { s -> s.executeUpdate("update after_sale_capacities set requested_quantity=requested_quantity+$quantity where order_item_id=$it") } }
            c.commit(); available
        } catch (t: Throwable) { c.rollback(); throw t }
    }
    private fun updateDecision(ds: javax.sql.DataSource): Boolean = ds.connection.use { c -> c.createStatement().use { it.executeUpdate("update after_sales set status='APPROVED',version=version+1 where id=1 and version=0") == 1 } }
    private fun insertReceipt(ds: javax.sql.DataSource, id: Long): Boolean = try { ds.connection.use { c -> c.createStatement().use { it.executeUpdate("insert into after_sale_command_receipts values($id,1,'CREATE','same','hash',1,'REQUESTED',now())") } }; true } catch (_: java.sql.SQLException) { false }
    private fun queryInt(c: Connection, sql: String): Int = c.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }
}
