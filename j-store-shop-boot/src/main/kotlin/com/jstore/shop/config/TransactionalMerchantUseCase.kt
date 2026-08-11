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
package com.jstore.shop.config

import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantRole
import com.jstore.shop.service.MerchantUseCase
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

class TransactionalMerchantUseCase(
    private val delegate: MerchantUseCase,
    transactionManager: PlatformTransactionManager,
) : MerchantUseCase {
    private val write = TransactionTemplate(transactionManager)
    private val read = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    override fun create(creatorUserId: Long, name: String) = tx {
        delegate.create(creatorUserId, name)
    }

    override fun listForUser(userId: Long) = query { delegate.listForUser(userId) }

    override fun addMember(
        actorUserId: Long,
        merchantId: MerchantId,
        userId: Long,
        roles: Set<MerchantRole>,
    ) = tx { delegate.addMember(actorUserId, merchantId, userId, roles) }

    override fun changeMemberRoles(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
        roles: Set<MerchantRole>,
    ) = tx { delegate.changeMemberRoles(actorUserId, merchantId, memberUserId, roles) }

    override fun disableMember(actorUserId: Long, merchantId: MerchantId, memberUserId: Long) = tx {
        delegate.disableMember(actorUserId, merchantId, memberUserId)
    }

    private fun <T> tx(block: () -> T): T = requireNotNull(write.execute { block() })

    private fun <T> query(block: () -> T): T = requireNotNull(read.execute { block() })
}
