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

import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.*
import com.jstore.user.domain.identity.*
import com.jstore.user.domain.useraccount.*
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

class ExternalAccountBindingServiceTest {
    private val accounts = mock<UserAccountRepository>()
    private val bindings = mock<ExternalIdentityBindingRepository>()
    private val factory = mock<UserAccountFactory>()
    private val phones = mock<PhoneVerificationGateway>()
    private val service =
        ExternalAccountBindingService(
            accounts,
            bindings,
            factory,
            mock<PasswordHasher>(),
            phones,
            mock<DomainEventPublisher>(),
        )
    private val identity = VerifiedWechatIdentity(ExternalIdentity("wechat", "app", "openid"), null)
    private val phone = PhoneNumber("+8613800138000")
    private val proof = PhoneVerificationProof("challenge", "123456")

    @Test
    fun `verified phone reuses local account without changing password or profile`() {
        whenever(phones.consumeChallenge(phone, proof, "wechat-binding", "intent")).thenReturn(true)
        val account = mock<UserAccount>()
        whenever(account.id).thenReturn(UserId(42))
        whenever(account.status).thenReturn(UserAccountStatus.ACTIVE)
        whenever(accounts.findByPhoneNumber(phone)).thenReturn(account)
        assertEquals(
            UserId(42),
            assertIs<Success<*>>(service.bind(identity, "intent", phone, proof, "", "")).value,
        )
        verify(bindings)
            .add(
                argThat {
                    userId == UserId(42) &&
                        this.identity == this@ExternalAccountBindingServiceTest.identity.identity
                }
            )
        verify(accounts, never()).add(any())
        verify(accounts, never()).save(any())
    }

    @Test
    fun `missing phone proof cannot create account or binding`() {
        assertIs<Failure<*>>(
            service.bind(identity, "intent", phone, proof, "nickname", "Password1")
        )
        verifyNoInteractions(bindings, factory)
    }

    @Test
    fun `existing binding to another account cannot be overwritten`() {
        whenever(phones.consumeChallenge(phone, proof, "wechat-binding", "intent")).thenReturn(true)
        val account = mock<UserAccount>()
        whenever(account.id).thenReturn(UserId(42))
        whenever(account.status).thenReturn(UserAccountStatus.ACTIVE)
        whenever(accounts.findByPhoneNumber(phone)).thenReturn(account)
        whenever(bindings.findByIdentity(identity.identity))
            .thenReturn(
                ExternalIdentityBinding(ExternalIdentityBindingId(), identity.identity, UserId(99))
            )
        assertIs<Failure<*>>(service.bind(identity, "intent", phone, proof, "", ""))
        verify(bindings, never()).add(any())
    }

    @Test
    fun `verified new phone creates a normal password account and an immutable binding`() {
        whenever(phones.consumeChallenge(phone, proof, "wechat-binding", "intent")).thenReturn(true)
        val account =
            UserAccountImpl(
                UserId(43),
                phone,
                Nickname("new-account"),
                Password("hash"),
                UserAccountStatus.ACTIVE,
            )
        whenever(factory.create(any(), any())).thenReturn(Success(account))
        assertEquals(
            UserId(43),
            assertIs<Success<*>>(
                    service.bind(identity, "intent", phone, proof, "new-account", "Password1")
                )
                .value,
        )
        verify(accounts).add(account)
        verify(bindings).add(argThat { userId == UserId(43) })
    }
}
