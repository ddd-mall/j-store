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
package com.jstore.outbox.operations

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("jstore.outbox.operations")
data class OutboxOperationsProperties(val adminUserIds: Set<Long> = emptySet()) {
    init {
        require(adminUserIds.all { it > 0 }) {
            "jstore.outbox.operations.admin-user-ids must contain only positive user IDs"
        }
    }

    fun isAdministrator(userId: Long): Boolean = userId in adminUserIds
}
