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
package com.jstore.security

import com.fasterxml.jackson.databind.ObjectMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.message.MapMessage

class Log4jMapMessageJsonSecurityTest {
    private val mapper = ObjectMapper()

    @Test
    fun `map message encodes finite and non-finite floating point values as valid JSON`() {
        val values = listOf(1.25, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)

        values.forEach { value ->
            val json = NumericMapMessage().with("value", value).toJson()
            val parsed = mapper.readTree(json)

            if (value.isFinite()) assertEquals(value, parsed["value"].doubleValue())
            else assertEquals(value.toString(), parsed["value"].textValue())
        }
    }

    @Test
    fun `log4j api remains bridged to slf4j`() {
        assertEquals(
            "org.apache.logging.slf4j.SLF4JLoggerContextFactory",
            LogManager.getFactory().javaClass.name,
        )
    }
}

private class NumericMapMessage : MapMessage<NumericMapMessage, Double>() {
    fun toJson(): String = StringBuilder().also(::asJson).toString()
}
