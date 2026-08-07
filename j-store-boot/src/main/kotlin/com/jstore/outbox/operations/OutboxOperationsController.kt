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

import com.jstore.authentication.annotation.CurrentUserId
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.common.framework.event.outbox.OutboxDeadLetterOperations
import com.jstore.user.domain.useraccount.UserId
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/outbox/dead-letters")
@RequireLogin
class OutboxOperationsController(
    private val deadLetterService: OutboxDeadLetterOperations,
    private val properties: OutboxOperationsProperties,
) {
    @GetMapping
    fun findDeadLetters(
        @CurrentUserId operatorId: UserId,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<*> {
        forbidden(operatorId)?.let {
            return it
        }
        if (page < 1 || size !in 1..200) {
            return ResponseEntity.badRequest()
                .body(
                    OutboxOperationsErrorResponse(
                        "page must be at least 1 and size must be between 1 and 200",
                        "OUTBOX_OPERATIONS_INVALID_PAGE",
                    )
                )
        }
        return ResponseEntity.ok(
            DeadLetterPageResponse.from(deadLetterService.findDeadLetters(page, size))
        )
    }

    @PostMapping("/requeue")
    fun requeue(
        @CurrentUserId operatorId: UserId,
        @Valid @RequestBody request: RequeueDeadLettersRequest,
    ): ResponseEntity<*> {
        forbidden(operatorId)?.let {
            return it
        }
        val result =
            deadLetterService.requeue(
                ids = request.ids,
                operatorId = operatorId.value.toString(),
                reason = request.reason,
            )
        return ResponseEntity.ok(RequeueDeadLettersResponse.from(result))
    }

    private fun forbidden(operatorId: UserId): ResponseEntity<OutboxOperationsErrorResponse>? {
        if (properties.isAdministrator(operatorId.value)) return null
        return ResponseEntity.status(403)
            .body(
                OutboxOperationsErrorResponse(
                    message = "Current user is not an Outbox operations administrator",
                    errorCode = "OUTBOX_OPERATIONS_FORBIDDEN",
                )
            )
    }
}
