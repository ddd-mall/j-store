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
package com.jstore.user.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.annotation.SkipLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.service.UserAccountUseCase
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
@RequireLogin
class UserAccountController(
    private val userAccountService: UserAccountUseCase,
    private val sessionStore: AccountSessionStore,
) {

    // ---- Request DTOs ----

    data class RegisterRequest(
        val phoneNumber: String,
        val nickname: String,
        val password: String,
        val challengeId: String,
        val verificationCode: String,
    )

    data class PhoneVerificationRequest(val phoneNumber: String)

    data class ChangeNicknameRequest(val nickname: String)

    data class ChangePasswordRequest(
        val oldPassword: String,
        val newPassword: String,
    )

    // ---- Response DTOs ----

    data class UserResponse(
        val id: Long,
        val phoneNumber: String,
        val nickname: String,
        val status: String,
        val createTime: LocalDateTime,
        val updateTime: LocalDateTime,
    )

    data class ErrorResponse(
        val message: String,
        val errorCode: String,
    )

    // ---- Endpoints ----

    @SkipLogin
    @PostMapping("/phone-verifications")
    fun requestPhoneVerification(
        @RequestBody request: PhoneVerificationRequest
    ): ResponseEntity<*> =
        userAccountService.requestPhoneVerification(PhoneNumber(request.phoneNumber)).toResponse {
            it
        }

    @SkipLogin
    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<*> {
        val cmd =
            UserRegisterCMD(
                phoneNumber = PhoneNumber(request.phoneNumber),
                nickname = request.nickname,
                rawPassword = request.password,
            )
        val proof = PhoneVerificationProof(request.challengeId, request.verificationCode)
        return userAccountService.register(cmd, proof).toResponse { account ->
            UserResponse(
                id = account.id.value,
                phoneNumber = account.phoneNumber.value,
                nickname = account.nickname.value,
                status = account.status.name,
                createTime = account.createTime,
                updateTime = account.updateTime,
            )
        }
    }

    @GetMapping("/me")
    fun findMe(@CurrentPrincipal principal: AuthenticatedPrincipal): ResponseEntity<*> {
        return userAccountService.findById(principal.userId()).toResponse { account ->
            UserResponse(
                id = account.id.value,
                phoneNumber = account.phoneNumber.value,
                nickname = account.nickname.value,
                status = account.status.name,
                createTime = account.createTime,
                updateTime = account.updateTime,
            )
        }
    }

    @PutMapping("/me/nickname")
    fun changeNickname(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @RequestBody request: ChangeNicknameRequest,
    ): ResponseEntity<*> {
        return userAccountService
            .changeNickname(
                userId = principal.userId(),
                newNickname = Nickname(request.nickname),
            )
            .toResponse {}
    }

    @PutMapping("/me/password")
    fun changePassword(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<*> {
        return userAccountService
            .changePassword(
                userId = principal.userId(),
                oldPassword = request.oldPassword,
                newPassword = request.newPassword,
            )
            .toResponse {}
    }

    @PostMapping("/me/logout")
    fun logout(@CurrentPrincipal principal: AuthenticatedPrincipal): ResponseEntity<*> {
        val session = requireNotNull(principal.session)
        sessionStore.revokeSession(principal.userId(), session.sessionId)
        return ResponseEntity.ok().build<Unit>()
    }

    // ---- Helper ----

    private fun AuthenticatedPrincipal.userId() = UserId(accountId.value)

    private fun <T> Result<T, BusinessError>.toResponse(mapper: (T) -> Any): ResponseEntity<*> {
        return fold(
            onSuccess = { ResponseEntity.ok(mapper(it)) },
            onFailure = { error ->
                ResponseEntity.status(error.httpCode)
                    .body(ErrorResponse(message = error.message, errorCode = error.errorCode))
            },
        )
    }
}
