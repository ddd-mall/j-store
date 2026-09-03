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
package com.jstore

import com.jstore.outbox.spring.persistence.OutboxEntryPOJpaRepository
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder

class JStoreApplicationContextTest {
    @Test
    fun `application registers domain and outbox repositories together`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            Flyway.configure()
                .dataSource(postgres.postgresDatabase)
                .locations("classpath:db/migration")
                .schemas("develop")
                .defaultSchema("develop")
                .load()
                .migrate()

            SpringApplicationBuilder(JStoreOrderBootApplication::class.java)
                .web(WebApplicationType.NONE)
                .run(
                    "--spring.main.lazy-initialization=true",
                    "--spring.flyway.enabled=false",
                    "--spring.datasource.url=${postgres.getJdbcUrl("postgres", "postgres")}&currentSchema=develop",
                    "--spring.datasource.username=postgres",
                    "--spring.datasource.password=",
                    "--jwt.access-secret=test-access-secret-with-at-least-32-bytes",
                    "--jwt.refresh-secret=test-refresh-secret-with-at-least-32-bytes",
                    "--jwt.issuer=j-store-test",
                    "--jwt.audience=j-store-test-clients",
                    "--jwt.key-id=test-key",
                    "--account.phone-verification.hmac-secret=test-phone-hmac-secret-with-at-least-32-bytes",
                    "--jstore.outbox.enabled=true",
                )
                .use { context ->
                    val orderRepositoryType =
                        Class.forName(
                            "com.jstore.order.domain.order.persistence.OrderPOJpaRepository"
                        )
                    assertThat(context.getBeansOfType(orderRepositoryType)).hasSize(1)
                    assertThat(context.getBean(OutboxEntryPOJpaRepository::class.java)).isNotNull
                }
        }
    }
}
