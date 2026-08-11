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
package com.jstore.outbox.operations

import com.fasterxml.jackson.databind.ObjectMapper
import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.spring.AuthenticationInterceptor
import com.jstore.outbox.spring.DeadLetterRequeueResult
import com.jstore.outbox.spring.OutboxDeadLetterOperations
import com.jstore.outbox.spring.OutboxDeadLetterPage
import com.jstore.user.domain.useraccount.TokenProvider
import com.jstore.user.domain.useraccount.TokenStore
import com.jstore.user.domain.useraccount.UserId
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

class OutboxOperationsControllerTest {
    private val service = FakeOperations()

    @Test
    fun `controller requires authentication`() {
        check(OutboxOperationsController::class.java.isAnnotationPresent(RequireLogin::class.java))
        val mvc =
            MockMvcBuilders.standaloneSetup(
                    OutboxOperationsController(service, OutboxOperationsProperties(setOf(7)))
                )
                .setCustomArgumentResolvers(CurrentUserResolver())
                .addInterceptors(
                    AuthenticationInterceptor(
                        mock<TokenProvider>(),
                        mock<TokenStore>(),
                        emptyList(),
                        ObjectMapper(),
                    )
                )
                .build()

        mvc.perform(get("/api/admin/outbox/dead-letters"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.errorCode").value("Auth.Token.Missing"))
    }

    @Test
    fun `empty administrator allowlist denies all authenticated users`() {
        val mvc = mvc(OutboxOperationsProperties())

        mvc.perform(get("/api/admin/outbox/dead-letters").header("X-Test-User", "7"))
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.errorCode").value("OUTBOX_OPERATIONS_FORBIDDEN"))
    }

    @Test
    fun `administrator can query payload-free dead-letter page`() {
        service.pageResult = OutboxDeadLetterPage(emptyList(), 1, 20, 0)
        val mvc = mvc(OutboxOperationsProperties(setOf(7)))

        mvc.perform(get("/api/admin/outbox/dead-letters").header("X-Test-User", "7"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.entries").isArray)
            .andExpect(jsonPath("$.page").value(1))
            .andExpect(jsonPath("$.payload").doesNotExist())
    }

    @Test
    fun `administrator requeue requires non-blank reason and records current operator`() {
        service.requeueResult = DeadLetterRequeueResult(1, 0)
        val mvc = mvc(OutboxOperationsProperties(setOf(7)))

        mvc.perform(
                post("/api/admin/outbox/dead-letters/requeue")
                    .header("X-Test-User", "7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ids":["entry-1"],"reason":"dependency recovered"}""")
            )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.requeuedCount").value(1))

        check(service.lastRequeue?.ids == listOf("entry-1"))
        check(service.lastRequeue?.operatorId == "7")
        check(service.lastRequeue?.reason == "dependency recovered")

        mvc.perform(
                post("/api/admin/outbox/dead-letters/requeue")
                    .header("X-Test-User", "7")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"ids":["entry-1"],"reason":" "}""")
            )
            .andExpect(status().isBadRequest)
    }

    private fun mvc(properties: OutboxOperationsProperties) =
        MockMvcBuilders.standaloneSetup(OutboxOperationsController(service, properties))
            .setCustomArgumentResolvers(CurrentUserResolver())
            .build()

    private class CurrentUserResolver : HandlerMethodArgumentResolver {
        override fun supportsParameter(parameter: MethodParameter) =
            parameter.hasParameterAnnotation(CurrentUserId::class.java)

        override fun resolveArgument(
            parameter: MethodParameter,
            mavContainer: ModelAndViewContainer?,
            webRequest: NativeWebRequest,
            binderFactory: org.springframework.web.bind.support.WebDataBinderFactory?,
        ) = UserId(webRequest.getHeader("X-Test-User")!!.toLong())
    }

    private class FakeOperations : OutboxDeadLetterOperations {
        var pageResult = OutboxDeadLetterPage(emptyList(), 1, 20, 0)
        var requeueResult = DeadLetterRequeueResult(0, 0)
        var lastRequeue: RequeueCall? = null

        override fun findDeadLetters(page: Int, size: Int) = pageResult

        override fun requeue(
            ids: Collection<String>,
            operatorId: String,
            reason: String,
            nextAttemptAt: java.time.Instant,
        ): DeadLetterRequeueResult {
            lastRequeue = RequeueCall(ids.toList(), operatorId, reason)
            return requeueResult
        }
    }

    private data class RequeueCall(
        val ids: List<String>,
        val operatorId: String,
        val reason: String,
    )
}
