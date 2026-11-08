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
package com.jstore.user.oidc

import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.UserId
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Transactional(propagation = Propagation.SUPPORTS, readOnly = true)
class JdbcExternalIdentityBindingRepository(private val jdbc: JdbcOperations) :
    ExternalIdentityBindingRepository {
    override fun findById(id: ExternalIdentityBindingId) =
        jdbc
            .query("SELECT * FROM user_external_identities WHERE id = ?", mapper, id.value)
            .firstOrNull()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun save(aggregate: ExternalIdentityBinding): ExternalIdentityBinding {
        add(aggregate)
        return aggregate
    }

    override fun findByIdentity(identity: ExternalIdentity) =
        jdbc
            .query(
                "SELECT * FROM user_external_identities WHERE provider = ? AND app_id = ? AND subject = ?",
                mapper,
                identity.provider,
                identity.appId,
                identity.subject,
            )
            .firstOrNull()

    override fun findByUser(userId: UserId, provider: String, appId: String) =
        jdbc
            .query(
                "SELECT * FROM user_external_identities WHERE user_id = ? AND provider = ? AND app_id = ?",
                mapper,
                userId.value,
                provider,
                appId,
            )
            .firstOrNull()

    @Transactional(propagation = Propagation.MANDATORY)
    override fun add(binding: ExternalIdentityBinding) {
        jdbc.update(
            "INSERT INTO user_external_identities(id, provider, app_id, subject, user_id, union_id) VALUES (?, ?, ?, ?, ?, ?)",
            binding.id.value,
            binding.identity.provider,
            binding.identity.appId,
            binding.identity.subject,
            binding.userId.value,
            binding.unionId,
        )
    }

    private val mapper =
        org.springframework.jdbc.core.RowMapper { rs, _ ->
            ExternalIdentityBinding(
                ExternalIdentityBindingId(rs.getString("id")),
                ExternalIdentity(
                    rs.getString("provider"),
                    rs.getString("app_id"),
                    rs.getString("subject"),
                ),
                UserId(rs.getLong("user_id")),
                rs.getString("union_id"),
            )
        }
}
