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
package com.jstore.user.migration

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.sql.SQLException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.init.ScriptUtils

class AccountSecurityMigrationTest {
    @Test
    fun `migration removes generated id default and supports E164 width`() {
        EmbeddedPostgres.builder().start().use { postgres ->
            postgres.postgresDatabase.connection.use { connection ->
                ScriptUtils.executeSqlScript(
                    connection,
                    ClassPathResource("db/migration/V20260507__baseline_j_store_boot_schema.sql"),
                )
                ScriptUtils.executeSqlScript(
                    connection,
                    ClassPathResource("db/migration/V20260808__account_security_hardening.sql"),
                )

                connection
                    .prepareStatement(
                        """
                        select column_default, character_maximum_length
                        from information_schema.columns
                        where table_schema = 'develop'
                          and table_name = 'user_accounts'
                          and column_name = ?
                        """
                            .trimIndent()
                    )
                    .use { statement ->
                        statement.setString(1, "id")
                        statement.executeQuery().use { rows ->
                            rows.next()
                            assertNull(rows.getString("column_default"))
                        }
                        statement.setString(1, "phone_number")
                        statement.executeQuery().use { rows ->
                            rows.next()
                            assertEquals(16, rows.getInt("character_maximum_length"))
                        }
                    }

                connection.createStatement().use { statement ->
                    statement.executeUpdate(
                        """
                        insert into develop.user_accounts(
                            id, phone_number, nickname, password_hash, status
                        ) values (9001, '+123456789012345', 'user', 'hash', 'ACTIVE')
                        """
                            .trimIndent()
                    )
                }

                assertFailsWith<SQLException> {
                    connection.createStatement().use { statement ->
                        statement.executeUpdate(
                            """
                            insert into develop.user_accounts(
                                phone_number, nickname, password_hash, status
                            ) values ('+14155552671', 'user2', 'hash', 'ACTIVE')
                            """
                                .trimIndent()
                        )
                    }
                }
            }
        }
    }
}
