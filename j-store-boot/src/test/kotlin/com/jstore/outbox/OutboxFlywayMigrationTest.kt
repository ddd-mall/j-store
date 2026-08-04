package com.jstore.outbox

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test

class OutboxFlywayMigrationTest {
    @Test
    fun `full production migrations create fencing and audit schema`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val dataSource = postgres.postgresDatabase
            val result =
                Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("develop")
                    .defaultSchema("develop")
                    .load()
                    .migrate()

            assertTrue(result.migrationsExecuted >= 4)
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT column_default, is_nullable FROM information_schema.columns " +
                                "WHERE table_schema='develop' AND table_name='outbox_entry' AND column_name='lock_token'"
                        )
                        .use { rows ->
                            assertTrue(rows.next())
                            assertEquals("0", rows.getString("column_default"))
                            assertEquals("NO", rows.getString("is_nullable"))
                        }
                    statement
                        .executeQuery("SELECT to_regclass('develop.outbox_dead_letter_audit')")
                        .use { rows ->
                            assertTrue(rows.next())
                            assertEquals("develop.outbox_dead_letter_audit", rows.getString(1))
                        }
                }
            }
        }
    }

    @Test
    fun `hardening migration preserves existing outbox states`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val dataSource = postgres.postgresDatabase
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .target(MigrationVersion.fromVersion("20260803"))
                .load()
                .migrate()
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    listOf("PENDING", "FAILED", "IN_PROGRESS", "DEAD_LETTER", "PUBLISHED")
                        .forEachIndexed { index, status ->
                            statement.executeUpdate(
                                "INSERT INTO develop.outbox_entry " +
                                    "(id,event_type,event_id,event_class_name,event_version,payload,aggregate_type,aggregate_id,status,created_at,updated_at,retry_count,next_attempt_at) " +
                                    "VALUES ('legacy-$index','test.event','event-$index','test.Event',1,'{}','Order','order-$index','$status',NOW(),NOW(),0,NOW())"
                            )
                        }
                }
            }

            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .load()
                .migrate()
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT status, lock_token FROM develop.outbox_entry WHERE id LIKE 'legacy-%'"
                        )
                        .use { rows ->
                            val states = mutableSetOf<String>()
                            while (rows.next()) {
                                states += rows.getString("status")
                                assertEquals(0L, rows.getLong("lock_token"))
                            }
                            assertEquals(
                                setOf(
                                    "PENDING",
                                    "FAILED",
                                    "IN_PROGRESS",
                                    "DEAD_LETTER",
                                    "PUBLISHED",
                                ),
                                states,
                            )
                        }
                }
            }
        }
    }
}
