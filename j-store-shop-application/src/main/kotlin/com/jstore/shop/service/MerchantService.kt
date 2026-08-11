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
package com.jstore.shop.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Result
import com.jstore.common.utils.Success
import com.jstore.common.utils.onFailure
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantErrors
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantMembershipId
import com.jstore.shop.domain.merchant.MerchantMembershipRepository
import com.jstore.shop.domain.merchant.MerchantPermission
import com.jstore.shop.domain.merchant.MerchantRepository
import com.jstore.shop.domain.merchant.MerchantRole
import com.jstore.shop.domain.merchant.MerchantStatus

fun interface MerchantIdGenerator {
    fun nextId(): Long
}

fun interface UserAccountLookup {
    fun exists(userId: Long): Boolean
}

data class MerchantAccountView(val merchant: Merchant, val membership: MerchantMembership)

interface MerchantUseCase {
    fun create(creatorUserId: Long, name: String): Result<Merchant, BusinessError>

    fun listForUser(userId: Long): List<MerchantAccountView>

    fun addMember(
        actorUserId: Long,
        merchantId: MerchantId,
        userId: Long,
        roles: Set<MerchantRole>,
    ): Result<MerchantMembership, BusinessError>

    fun changeMemberRoles(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
        roles: Set<MerchantRole>,
    ): Result<MerchantMembership, BusinessError>

    fun disableMember(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
    ): Result<Unit, BusinessError>
}

class MerchantAuthorizationService(
    private val merchantRepository: MerchantRepository,
    private val membershipRepository: MerchantMembershipRepository,
) {
    fun hasPermission(
        userId: Long,
        merchantId: MerchantId,
        permission: MerchantPermission,
    ): Boolean {
        val merchant = merchantRepository.findById(merchantId) ?: return false
        if (merchant.status != MerchantStatus.ACTIVE) return false
        return membershipRepository.findByMerchantAndUser(merchantId, userId)?.allows(permission) ==
            true
    }
}

class MerchantService(
    private val idGenerator: MerchantIdGenerator,
    private val merchantRepository: MerchantRepository,
    private val membershipRepository: MerchantMembershipRepository,
    private val userAccountLookup: UserAccountLookup,
) : MerchantUseCase {
    private val authorization =
        MerchantAuthorizationService(merchantRepository, membershipRepository)

    override fun create(creatorUserId: Long, name: String): Result<Merchant, BusinessError> {
        if (!userAccountLookup.exists(creatorUserId)) return Failure(MerchantErrors.USER_NOT_FOUND)
        if (!Merchant.validName(name.trim())) return Failure(MerchantErrors.NAME_INVALID)
        val merchant = Merchant(MerchantId(idGenerator.nextId()), name)
        val owner =
            MerchantMembership(
                MerchantMembershipId(idGenerator.nextId()),
                merchant.id,
                creatorUserId,
                setOf(MerchantRole.OWNER),
            )
        return Success(merchantRepository.createWithOwner(merchant, owner))
    }

    override fun listForUser(userId: Long): List<MerchantAccountView> =
        membershipRepository.findByUser(userId).mapNotNull { membership ->
            merchantRepository.findById(membership.merchantId)?.let {
                MerchantAccountView(it, membership)
            }
        }

    override fun addMember(
        actorUserId: Long,
        merchantId: MerchantId,
        userId: Long,
        roles: Set<MerchantRole>,
    ): Result<MerchantMembership, BusinessError> {
        authorizeMemberManagement(actorUserId, merchantId).onFailure {
            return Failure(it)
        }
        if (!userAccountLookup.exists(userId)) return Failure(MerchantErrors.USER_NOT_FOUND)
        if (roles.isEmpty()) return Failure(MerchantErrors.ROLES_EMPTY)
        if (MerchantRole.OWNER in roles) return Failure(MerchantErrors.OWNER_ROLE_RESERVED)
        if (membershipRepository.findByMerchantAndUser(merchantId, userId) != null) {
            return Failure(MerchantErrors.MEMBER_ALREADY_EXISTS)
        }
        val membership =
            MerchantMembership(
                MerchantMembershipId(idGenerator.nextId()),
                merchantId,
                userId,
                roles,
            )
        return Success(membershipRepository.save(membership))
    }

    override fun changeMemberRoles(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
        roles: Set<MerchantRole>,
    ): Result<MerchantMembership, BusinessError> {
        authorizeMemberManagement(actorUserId, merchantId).onFailure {
            return Failure(it)
        }
        val membership =
            membershipRepository.findByMerchantAndUser(merchantId, memberUserId)
                ?: return Failure(MerchantErrors.MEMBER_NOT_FOUND)
        membership.changeRoles(roles).onFailure {
            return Failure(it)
        }
        return Success(membershipRepository.save(membership))
    }

    override fun disableMember(
        actorUserId: Long,
        merchantId: MerchantId,
        memberUserId: Long,
    ): Result<Unit, BusinessError> {
        authorizeMemberManagement(actorUserId, merchantId).onFailure {
            return Failure(it)
        }
        val membership =
            membershipRepository.findByMerchantAndUser(merchantId, memberUserId)
                ?: return Failure(MerchantErrors.MEMBER_NOT_FOUND)
        membership.disable().onFailure {
            return Failure(it)
        }
        membershipRepository.save(membership)
        return Success(Unit)
    }

    private fun authorizeMemberManagement(
        userId: Long,
        merchantId: MerchantId,
    ): Result<Unit, BusinessError> =
        if (authorization.hasPermission(userId, merchantId, MerchantPermission.MEMBER_MANAGE)) {
            Success(Unit)
        } else {
            Failure(MerchantErrors.FORBIDDEN)
        }
}
