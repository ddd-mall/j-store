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
package com.jstore.user.service

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.framework.event.publishPendingEvents
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.*
import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD

/**
 * Caller owns a short database transaction; external identity comes only from the server-side
 * exchange.
 */
class ExternalAccountBindingService(
    private val accounts: UserAccountRepository,
    private val bindings: ExternalIdentityBindingRepository,
    private val factory: UserAccountFactory,
    private val hasher: PasswordHasher,
    private val phones: PhoneVerificationGateway,
    private val events: DomainEventPublisher,
) {
  fun bind(
      verified: VerifiedWechatIdentity,
      intentId: String,
      phone: PhoneNumber,
      proof: PhoneVerificationProof,
      nickname: String,
      password: String,
  ): Result<UserId, BusinessError> {
    if (!phones.consumeChallenge(phone, proof, "wechat-binding", intentId))
        return Failure(UserAccountErrors.PHONE_VERIFICATION_INVALID)
    val existing = accounts.findByPhoneNumber(phone)
    if (existing != null && existing.status != UserAccountStatus.ACTIVE)
        return Failure(UserAccountErrors.ACCOUNT_DISABLED)
    val previous = bindings.findByIdentity(verified.identity)
    if (previous != null && (existing == null || !previous.belongsTo(existing.id)))
        return Failure(CONFLICT)
    if (
        existing != null &&
            bindings
                .findByUser(existing.id, verified.identity.provider, verified.identity.appId)
                ?.identity
                ?.let { it != verified.identity } == true
    )
        return Failure(CONFLICT)
    if (previous != null) return Success(previous.userId)
    val account =
        existing
            ?: when (
                val created = factory.create(UserRegisterCMD(phone, nickname, password), hasher)
            ) {
              is Failure -> return created
              is Success -> created.value.also(accounts::add)
            }
    bindings.add(
        ExternalIdentityBinding(
            ExternalIdentityBindingId(),
            verified.identity,
            account.id,
            verified.unionId,
        )
    )
    if (existing == null) account.publishPendingEvents(events)
    return Success(account.id)
  }

  companion object {
    val CONFLICT = BusinessError("微信身份已绑定或账号已有其他微信身份", "User.ExternalIdentity.Conflict", 409)
  }
}
