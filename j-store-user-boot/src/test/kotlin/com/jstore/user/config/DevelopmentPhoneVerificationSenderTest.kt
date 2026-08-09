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
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class DevelopmentPhoneVerificationSenderTest {
    @Test
    fun `development sender does not log verification code or complete phone number`(
        output: CapturedOutput
    ) {
        DevelopmentPhoneVerificationSender()
            .send(
                PhoneNumber("+8613800138000"),
                "739241",
            )

        assertFalse(output.out.contains("739241"))
        assertFalse(output.out.contains("+8613800138000"))
        assertTrue(output.out.contains("+86138****8000"))
    }
}
