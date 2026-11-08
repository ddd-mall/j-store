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
package com.jstore.user.oidc

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletResponse
import kotlin.test.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import org.springframework.mock.web.*
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus

class ProtocolTransactionFilterTest {
    @Test
    fun `failed persistence cannot leak credentials or a redirect`() {
        val manager = mock<PlatformTransactionManager>()
        whenever(manager.getTransaction(any())).thenReturn(mock<TransactionStatus>())
        doThrow(IllegalStateException("commit failed")).whenever(manager).commit(any())
        val response = MockHttpServletResponse()
        ProtocolTransactionFilter(manager)
            .doFilter(
                MockHttpServletRequest(),
                response,
                FilterChain { _, target ->
                    val http = target as HttpServletResponse
                    http.writer.write("secret-credential")
                    http.sendRedirect("https://client.example?code=secret-code")
                },
            )
        assertEquals(503, response.status)
        assertNull(response.getHeader("Location"))
        assertFalse(response.contentAsString.contains("secret"))
    }

    @Test
    fun `unsupported proof bound token requests never reach protocol processing`() {
        val request = MockHttpServletRequest().apply { addHeader("DPoP", "proof") }
        val response = MockHttpServletResponse()
        var processed = false
        BearerOnlyProtocolFilter()
            .doFilter(request, response, FilterChain { _, _ -> processed = true })
        assertEquals(400, response.status)
        assertFalse(processed)
    }
}
