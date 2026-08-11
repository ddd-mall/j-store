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
package com.jstore.user.domain.useraccount

import com.jstore.common.properties.PhoneNumber
import java.time.Instant

data class PhoneVerificationProof(val challengeId: String, val code: String)

data class PhoneVerificationChallenge(val challengeId: String, val expiresAt: Instant)

data class IssuedPhoneVerificationChallenge(
    val challenge: PhoneVerificationChallenge,
    val code: String,
)

interface PhoneVerificationGateway {
    /** Returns null when the phone number is currently send-rate-limited. */
    fun createChallenge(phoneNumber: PhoneNumber): IssuedPhoneVerificationChallenge?

    fun consumeChallenge(
        phoneNumber: PhoneNumber,
        proof: PhoneVerificationProof,
    ): Boolean
}

interface PhoneVerificationCodeSender {
    fun send(phoneNumber: PhoneNumber, code: String)
}

interface LoginAttemptGuard {
    fun isAllowed(phoneNumber: PhoneNumber): Boolean

    fun recordFailure(phoneNumber: PhoneNumber)

    fun reset(phoneNumber: PhoneNumber)
}
