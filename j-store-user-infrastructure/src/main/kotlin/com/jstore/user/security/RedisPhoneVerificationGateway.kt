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
package com.jstore.user.security

import com.jstore.common.properties.PhoneNumber
import com.jstore.user.domain.useraccount.IssuedPhoneVerificationChallenge
import com.jstore.user.domain.useraccount.PhoneVerificationChallenge
import com.jstore.user.domain.useraccount.PhoneVerificationGateway
import com.jstore.user.domain.useraccount.PhoneVerificationProof
import java.security.SecureRandom
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript

class RedisPhoneVerificationGateway(
    private val redisTemplate: StringRedisTemplate,
    hmacSecret: String,
    private val challengeTtlSeconds: Long = 300L,
    private val sendIntervalSeconds: Long = 60L,
) : PhoneVerificationGateway {
    private val hmacKey = SecretKeySpec(hmacSecret.toByteArray(Charsets.UTF_8), "HmacSHA256")
    private val random = SecureRandom()

    init {
        require(hmacSecret.toByteArray(Charsets.UTF_8).size >= 32) {
            "phone verification HMAC secret must be at least 32 bytes"
        }
        require(challengeTtlSeconds > 0) { "challenge TTL must be positive" }
        require(sendIntervalSeconds > 0) { "send interval must be positive" }
    }

    override fun createChallenge(phoneNumber: PhoneNumber): IssuedPhoneVerificationChallenge? {
        val challengeId = randomBytesHex(24)
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val stored =
            redisTemplate.execute(
                CREATE_SCRIPT,
                listOf(rateKey(phoneNumber), challengeKey(challengeId)),
                digest(challengeId, phoneNumber, code),
                challengeTtlSeconds.toString(),
                sendIntervalSeconds.toString(),
            ) == 1L
        if (!stored) return null
        return IssuedPhoneVerificationChallenge(
            PhoneVerificationChallenge(
                challengeId = challengeId,
                expiresAt = Instant.now().plusSeconds(challengeTtlSeconds),
            ),
            code,
        )
    }

    override fun consumeChallenge(
        phoneNumber: PhoneNumber,
        proof: PhoneVerificationProof,
    ): Boolean {
        if (
            proof.challengeId.isBlank() || proof.code.length != 6 || !proof.code.all(Char::isDigit)
        ) {
            return false
        }
        return redisTemplate.execute(
            CONSUME_SCRIPT,
            listOf(challengeKey(proof.challengeId)),
            digest(proof.challengeId, phoneNumber, proof.code),
        ) == 1L
    }

    private fun digest(challengeId: String, phoneNumber: PhoneNumber, code: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(hmacKey)
        return mac.doFinal("$challengeId:${phoneNumber.value}:$code".toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun randomBytesHex(size: Int): String =
        ByteArray(size).also(random::nextBytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }

    private fun challengeKey(challengeId: String) = "$CHALLENGE_KEY_PREFIX$challengeId"

    private fun rateKey(phoneNumber: PhoneNumber) = "$RATE_KEY_PREFIX${phoneNumber.value}"

    private companion object {
        const val CHALLENGE_KEY_PREFIX = "phone_verification:"
        const val RATE_KEY_PREFIX = "phone_verification_rate:"
        val CREATE_SCRIPT =
            DefaultRedisScript(
                """
                if redis.call('EXISTS', KEYS[1]) == 1 then return 0 end
                redis.call('SET', KEYS[1], '1', 'EX', ARGV[3])
                redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[2])
                return 1
                """
                    .trimIndent(),
                Long::class.java,
            )

        val CONSUME_SCRIPT =
            DefaultRedisScript(
                """
                local stored = redis.call('GET', KEYS[1])
                if not stored then return 0 end
                redis.call('DEL', KEYS[1])
                if stored == ARGV[1] then return 1 end
                return 0
                """
                    .trimIndent(),
                Long::class.java,
            )
    }
}
