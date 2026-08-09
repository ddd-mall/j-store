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
import com.jstore.user.domain.useraccount.PhoneVerificationCodeSender
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local", "dev")
class DevelopmentPhoneVerificationSender : PhoneVerificationCodeSender {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun send(phoneNumber: PhoneNumber, code: String) {
        val nationalNumber = phoneNumber.nationalNumber
        val maskedNumber =
            if (nationalNumber.length > 7) {
                "+${phoneNumber.countryCallingCode}${nationalNumber.take(3)}****${nationalNumber.takeLast(4)}"
            } else {
                "+${phoneNumber.countryCallingCode}****"
            }
        logger.info("Development phone verification code issued for {}", maskedNumber)
    }
}
