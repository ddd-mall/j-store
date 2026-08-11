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
package com.jstore.user.config

import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.AuthTokenClaims
import com.jstore.user.domain.useraccount.AuthTokenPair
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import com.jstore.user.service.RefreshTokenDigest
import com.jstore.user.service.UserAccountUseCase
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFailsWith
import org.mockito.kotlin.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class TransactionalUserAccountUseCaseTest {
    private val delegate = mock<UserAccountUseCase>()
    private val tokenProvider = mock<TokenProvider>()
    private val tokenStore = mock<TokenStore>()
    private val transactionManager = mock<PlatformTransactionManager>()
    private val transactionStatus = mock<TransactionStatus>()
    private val userId = UserId(42)

    init {
        whenever(transactionManager.getTransaction(any())).thenReturn(transactionStatus)
    }

    @Test
    fun `login stores refresh digest only after database transaction commits`() {
        val phone = PhoneNumber("+8613800138000")
        val tokens = AuthTokenPair("access", LocalDateTime.now(), "refresh", LocalDateTime.now())
        val claims = AuthTokenClaims(userId, "session-1", 5L, "jti")
        whenever(delegate.login(phone, "password")).thenReturn(Success(tokens))
        whenever(tokenProvider.parseRefreshToken("refresh")).thenReturn(claims)

        subject().login(phone, "password")

        inOrder(transactionManager, tokenStore) {
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore)
                .storeRefreshSession(
                    userId,
                    "session-1",
                    RefreshTokenDigest.sha256("refresh"),
                    5L,
                    604800L,
                )
        }
    }

    @Test
    fun `password change disable and force offline revoke all sessions before commit`() {
        whenever(delegate.changePassword(userId, "old", "NewPass12")).thenReturn(Success(Unit))
        whenever(delegate.disable(userId)).thenReturn(Success(Unit))
        whenever(delegate.forceOffline(userId)).thenReturn(Success(Unit))

        subject().changePassword(userId, "old", "NewPass12")
        subject().disable(userId)
        subject().forceOffline(userId)

        inOrder(tokenStore, transactionManager) {
            verify(tokenStore).revokeAllSessions(userId)
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore).revokeAllSessions(userId)
            verify(transactionManager).commit(transactionStatus)
            verify(tokenStore).revokeAllSessions(userId)
            verify(transactionManager).commit(transactionStatus)
        }
    }

    @Test
    fun `session revocation failure rolls back password change`() {
        whenever(delegate.changePassword(userId, "old", "NewPass12")).thenReturn(Success(Unit))
        whenever(tokenStore.revokeAllSessions(userId))
            .thenThrow(IllegalStateException("Redis unavailable"))

        assertFailsWith<IllegalStateException> {
            subject().changePassword(userId, "old", "NewPass12")
        }

        verify(transactionManager).rollback(transactionStatus)
        verify(transactionManager, never()).commit(transactionStatus)
    }

    private fun subject() =
        TransactionalUserAccountUseCase(delegate, tokenProvider, tokenStore, transactionManager)
}
