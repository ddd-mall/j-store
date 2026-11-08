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
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.*
import com.jstore.user.domain.useraccount.*

/** Validates local credentials without minting tokens or creating browser sessions. */
class AccountCredentialsService(
    private val accounts: UserAccountRepository,
    private val hasher: PasswordHasher,
    private val attempts: LoginAttemptGuard,
) {
  fun authenticate(phone: PhoneNumber, password: String): Result<UserId, BusinessError> {
    if (!attempts.isAllowed(phone)) return Failure(UserAccountErrors.LOGIN_RATE_LIMITED)
    val account = accounts.findByPhoneNumber(phone)
    if (account == null || !hasher.matches(password, account.passwordHash.hashedValue)) {
      attempts.recordFailure(phone)
      return Failure(UserAccountErrors.INVALID_CREDENTIALS)
    }
    if (account.status != UserAccountStatus.ACTIVE)
        return Failure(UserAccountErrors.ACCOUNT_DISABLED)
    attempts.reset(phone)
    return Success(account.id)
  }
}
