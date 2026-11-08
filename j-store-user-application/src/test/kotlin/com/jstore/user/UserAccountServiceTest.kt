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
package com.jstore.user

import com.jstore.common.errors.BusinessError
import com.jstore.common.framework.event.DomainEventPublisher
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Failure
import com.jstore.common.utils.Success
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.service.UserAccountService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.mockito.kotlin.*

class UserAccountServiceTest :
    FunSpec({
        lateinit var factory: UserAccountFactory
        lateinit var repository: UserAccountRepository
        lateinit var passwordHasher: PasswordHasher
        lateinit var tokenStore: AccountSessionStore
        lateinit var verificationGateway: PhoneVerificationGateway
        lateinit var codeSender: PhoneVerificationCodeSender
        lateinit var loginGuard: LoginAttemptGuard
        lateinit var eventPublisher: DomainEventPublisher
        lateinit var service: UserAccountService

        val phone = PhoneNumber("+8613800000001")
        val userId = UserId(1L)

        fun activeAccount(status: UserAccountStatus = UserAccountStatus.ACTIVE) =
            UserAccountImpl(
                id = userId,
                phoneNumber = phone,
                nickname = Nickname("testuser"),
                passwordHash = Password("hashed_pw"),
                status = status,
            )

        beforeEach {
            factory = mock()
            repository = mock()
            passwordHasher = mock()
            tokenStore = mock()
            verificationGateway = mock()
            codeSender = mock()
            loginGuard = mock()
            eventPublisher = mock()
            whenever(loginGuard.isAllowed(any())).thenReturn(true)
            service =
                UserAccountService(
                    factory,
                    repository,
                    passwordHasher,
                    verificationGateway,
                    codeSender,
                    eventPublisher,
                )
        }

        test("request phone verification sends code but only returns public challenge") {
            val challenge =
                PhoneVerificationChallenge("challenge-1", java.time.Instant.now().plusSeconds(300))
            whenever(verificationGateway.createChallenge(phone))
                .thenReturn(IssuedPhoneVerificationChallenge(challenge, "123456"))

            val result = service.requestPhoneVerification(phone)

            result.shouldBeInstanceOf<Success<PhoneVerificationChallenge>>()
            result.value shouldBe challenge
            verify(codeSender).send(phone, "123456")
        }

        test("register consumes phone-bound proof before creating account") {
            val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
            val proof = PhoneVerificationProof("challenge-1", "123456")
            val account = mock<UserAccount> { on { id } doReturn userId }
            whenever(verificationGateway.consumeChallenge(phone, proof)).thenReturn(true)
            whenever(repository.existsByPhoneNumber(phone)).thenReturn(false)
            whenever(factory.create(cmd, passwordHasher)).thenReturn(Success(account))

            val result = service.register(cmd, proof)

            result.shouldBeInstanceOf<Success<UserAccount>>()
            inOrder(verificationGateway, repository) {
                verify(verificationGateway).consumeChallenge(phone, proof)
                verify(repository).add(account)
            }
        }

        test("invalid phone proof cannot create an account") {
            val cmd = UserRegisterCMD(phone, "nick", "Pass1234")
            val proof = PhoneVerificationProof("challenge-1", "000000")
            whenever(verificationGateway.consumeChallenge(phone, proof)).thenReturn(false)

            val result = service.register(cmd, proof)

            result.shouldBeInstanceOf<Failure<BusinessError>>()
            result.error shouldBe UserAccountErrors.PHONE_VERIFICATION_INVALID
            verify(repository, never()).add(any())
        }
    })
