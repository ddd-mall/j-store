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
package com.jstore.user.domain.identity

import com.jstore.common.framework.AggregateRoot
import com.jstore.common.framework.Identifier
import com.jstore.user.domain.useraccount.UserId
import java.util.UUID

data class ExternalIdentity(val provider: String, val appId: String, val subject: String) {
  init {
    require(provider == "wechat")
    require(appId.isNotBlank() && appId.length <= 128 && appId.none(Char::isWhitespace))
    require(subject.isNotBlank() && subject.length <= 128 && subject.none(Char::isWhitespace))
  }
}

data class ExternalIdentityBindingId(val value: String = UUID.randomUUID().toString()) : Identifier

class ExternalIdentityBinding(
    override val id: ExternalIdentityBindingId,
    val identity: ExternalIdentity,
    val userId: UserId,
    val unionId: String? = null,
) : AggregateRoot<ExternalIdentityBindingId> {
  init {
    require(unionId == null || (unionId.isNotBlank() && unionId.length <= 128))
  }

  fun belongsTo(userId: UserId) = this.userId == userId
}

interface ExternalIdentityBindingRepository :
    com.jstore.common.framework.AggregateRepository<
        ExternalIdentityBindingId,
        ExternalIdentityBinding,
    > {
  fun findByIdentity(identity: ExternalIdentity): ExternalIdentityBinding?

  fun findByUser(userId: UserId, provider: String, appId: String): ExternalIdentityBinding?

  fun add(binding: ExternalIdentityBinding)
}

fun interface WechatIdentityGateway {
  fun exchange(code: String): VerifiedWechatIdentity?
}

data class VerifiedWechatIdentity(val identity: ExternalIdentity, val unionId: String?)
