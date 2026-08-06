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
