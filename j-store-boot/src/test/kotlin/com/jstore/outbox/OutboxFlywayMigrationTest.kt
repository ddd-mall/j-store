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
package com.jstore.outbox

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
                    statement
                        .executeQuery(
                            "SELECT message_kind, delivery_target, destination, logical_destination, " +
                                "delivery_profile, accept_before, published_at, partition_key, correlation_id, " +
                                "transport_id, ordering_key, sequence_no " +
                                "FROM develop.outbox_entry LIMIT 0"
                        )
                        .use { rows ->
                            assertEquals(12, rows.metaData.columnCount)
                        }
                    statement
                        .executeQuery("SELECT to_regclass('develop.outbox_stream_position')")
                        .use { rows ->
                            assertTrue(rows.next())
                            assertEquals("develop.outbox_stream_position", rows.getString(1))
                        }
                    listOf(
                            "idx_domain_event_consumption_retention",
                            "idx_message_stream_consumption_retention",
                        )
                        .forEach { indexName ->
                            statement
                                .executeQuery("SELECT to_regclass('develop.$indexName')")
                                .use { rows ->
                                    assertTrue(rows.next())
                                    assertEquals("develop.$indexName", rows.getString(1))
                                }
                        }
                    val removedTimerTables =
                        listOf("timer_job", "handled_timer_job", "timer_job_dead_queue")
                    removedTimerTables.forEach { table ->
                        statement.executeQuery("SELECT to_regclass('develop.$table')").use { rows ->
                            assertTrue(rows.next())
                            assertNull(
                                rows.getString(1),
                                "$table must not exist in the target schema",
                            )
                        }
                    }
                    statement
                        .executeQuery(
                            "SELECT COUNT(*) FROM pg_constraint " +
                                "WHERE conrelid = 'develop.outbox_entry'::regclass " +
                                "AND conname IN ('chk_outbox_publication_time', " +
                                "'chk_outbox_acceptance_deadline_kind', " +
                                "'chk_outbox_domain_delivery_metadata')"
                        )
                        .use { rows ->
                            assertTrue(rows.next())
                            assertEquals(3L, rows.getLong(1))
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
                .target(MigrationVersion.fromVersion("20260811"))
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

    @Test
    fun `ordering migration backfills deterministic transport scoped stream positions`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            val dataSource = postgres.postgresDatabase
            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .target(MigrationVersion.fromVersion("20260810"))
                .load()
                .migrate()
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        INSERT INTO develop.outbox_entry
                            (id,event_type,event_id,event_class_name,event_version,payload,
                             aggregate_type,aggregate_id,status,created_at,updated_at,retry_count,
                             next_attempt_at,message_kind,delivery_target,destination,partition_key,
                             correlation_id,transport_id,occurred_at,lock_token)
                        VALUES
                            ('local-1','order.event','event-1','test.Event',1,'{}','Order','42',
                             'PUBLISHED','2026-01-01','2026-01-01',0,'2026-01-01','INTEGRATION_EVENT',
                             'LOCAL_INTEGRATION','orders.events','42','event-1','local','2026-01-01',0),
                            ('local-2','order.event','event-2','test.Event',1,'{}','Order','42',
                             'PENDING','2026-01-02','2026-01-02',0,'2026-01-02','INTEGRATION_EVENT',
                             'LOCAL_INTEGRATION','orders.events','42','event-2','local','2026-01-02',0),
                            ('kafka-1','order.event','event-1','test.Event',1,'{}','Order','42',
                             'PENDING','2026-01-01','2026-01-01',0,'2026-01-01','INTEGRATION_EVENT',
                             'BROKER','orders.events','42','event-1','kafka','2026-01-01',0),
                            ('rabbit-1','order.event','event-3','test.Event',1,'{}','Order','43',
                             'PENDING','2026-01-01','2026-01-01',0,'2026-01-01','INTEGRATION_EVENT',
                             'BROKER','订单🚀','客户🚀','event-3','rabbit','2026-01-01',0)
                        """
                            .trimIndent()
                    )
                }
            }

            Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .target(MigrationVersion.fromVersion("20260811"))
                .load()
                .migrate()

            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement
                        .executeQuery(
                            "SELECT id, ordering_key, sequence_no FROM develop.outbox_entry " +
                                "ORDER BY id"
                        )
                        .use { rows ->
                            val positions = linkedMapOf<String, Pair<String, Long>>()
                            while (rows.next()) {
                                positions[rows.getString(1)] = rows.getString(2) to rows.getLong(3)
                            }
                            val expectedOrderingKey =
                                "37765866ca10be5fc944917ff7648513d69eb97a6ee17b0d829c57450fe2f10f"
                            assertEquals(expectedOrderingKey to 1L, positions["kafka-1"])
                            assertEquals(expectedOrderingKey to 1L, positions["local-1"])
                            assertEquals(expectedOrderingKey to 2L, positions["local-2"])
                            assertEquals(
                                "f1a476be28e1db6b844cd08b189fdb4dab3db81545105da5711f10d336aa9193" to
                                    1L,
                                positions["rabbit-1"],
                            )
                        }
                    statement
                        .executeQuery(
                            "SELECT transport_id, last_sequence_no " +
                                "FROM develop.outbox_stream_position ORDER BY transport_id"
                        )
                        .use { rows ->
                            val positions = linkedMapOf<String, Long>()
                            while (rows.next()) positions[rows.getString(1)] = rows.getLong(2)
                            assertEquals(
                                mapOf("kafka" to 1L, "local" to 2L, "rabbit" to 1L),
                                positions,
                            )
                        }
                    statement
                        .executeQuery(
                            "SELECT last_sequence_no FROM develop.message_stream_consumption " +
                                "WHERE consumer_id = 'jstore.local-integration-bus' " +
                                "AND transport_id = 'local'"
                        )
                        .use { rows ->
                            assertTrue(rows.next())
                            assertEquals(1L, rows.getLong(1))
                        }
                }
            }
        }
    }
}
