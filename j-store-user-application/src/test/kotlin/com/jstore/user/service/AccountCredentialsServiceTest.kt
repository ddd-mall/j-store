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

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class AccountCredentialsServiceTest {
    private val accounts = mock<UserAccountRepository>()
    private val hasher = mock<PasswordHasher>()
    private val attempts = mock<LoginAttemptGuard>()
    private val phone = PhoneNumber("+8613800138000")
    private val service = AccountCredentialsService(accounts, hasher, attempts)

    @Test
    fun `unknown phone and wrong password have same failure and increment guard`() {
        whenever(attempts.isAllowed(phone)).thenReturn(true)
        assertEquals(
            UserAccountErrors.INVALID_CREDENTIALS,
            assertIs<Failure<*>>(service.authenticate(phone, "wrong")).error,
        )
        verify(attempts).recordFailure(phone)
    }

    @Test
    fun `valid local credentials return stable identity without issuing credentials`() {
        val account = mock<UserAccount>()
        whenever(account.id).thenReturn(UserId(42))
        whenever(account.passwordHash).thenReturn(Password("hash"))
        whenever(account.status).thenReturn(UserAccountStatus.ACTIVE)
        whenever(accounts.findByPhoneNumber(phone)).thenReturn(account)
        whenever(attempts.isAllowed(phone)).thenReturn(true)
        whenever(hasher.matches("password1", "hash")).thenReturn(true)
        assertEquals(
            UserId(42),
            assertIs<Success<*>>(service.authenticate(phone, "password1")).value,
        )
        verify(attempts).reset(phone)
        verify(accounts, never()).save(any())
    }
}
