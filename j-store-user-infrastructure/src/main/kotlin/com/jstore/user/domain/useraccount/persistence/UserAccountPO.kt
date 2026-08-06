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
package com.jstore.user.domain.useraccount.persistence

import com.jstore.user.domain.useraccount.UserAccountStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "user_accounts")
class UserAccountPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "phone_number", nullable = false, unique = true, length = 16)
    var phoneNumber: String = "",
    @Column(name = "nickname", nullable = false, length = 20) var nickname: String = "",
    @Column(name = "password_hash", nullable = false, length = 255) var passwordHash: String = "",
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: UserAccountStatus = UserAccountStatus.ACTIVE,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
