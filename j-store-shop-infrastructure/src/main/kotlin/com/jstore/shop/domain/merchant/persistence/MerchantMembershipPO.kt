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
package com.jstore.shop.domain.merchant.persistence

import com.jstore.shop.domain.merchant.MerchantMembershipStatus
import com.jstore.shop.domain.merchant.MerchantRole
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

@Entity
@Table(
    name = "merchant_memberships",
    uniqueConstraints =
        [
            UniqueConstraint(
                name = "uk_merchant_memberships_merchant_user",
                columnNames = ["merchant_id", "user_id"],
            )
        ],
)
class MerchantMembershipPO(
    @Id @Column(name = "id") var id: Long = 0,
    @Column(name = "merchant_id", nullable = false) var merchantId: Long = 0,
    @Column(name = "user_id", nullable = false) var userId: Long = 0,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "merchant_membership_roles",
        joinColumns = [JoinColumn(name = "membership_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 32)
    var roles: MutableSet<MerchantRole> = linkedSetOf(),
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    var status: MerchantMembershipStatus = MerchantMembershipStatus.ACTIVE,
    @Column(name = "create_time", nullable = false)
    var createTime: LocalDateTime = LocalDateTime.now(),
    @Column(name = "update_time", nullable = false)
    var updateTime: LocalDateTime = LocalDateTime.now(),
)
