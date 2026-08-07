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

import com.jstore.common.errors.BusinessError
import com.jstore.common.properties.PhoneNumber
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.user.domain.useraccount.*
import com.jstore.user.domain.useraccount.command.UserRegisterCMD
import com.jstore.user.service.UserAccountService
import java.time.LocalDateTime
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/users")
class UserAccountController(private val userAccountService: UserAccountService) {

    // ---- Request DTOs ----

    data class RegisterRequest(
        val phoneNumber: String,
        val nickname: String,
        val password: String,
    )

    data class LoginRequest(
        val phoneNumber: String,
        val password: String,
    )

    data class RefreshTokenRequest(val refreshToken: String)

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

    data class TokenResponse(
        val accessToken: String,
        val accessTokenExpiresAt: LocalDateTime,
        val refreshToken: String,
        val refreshTokenExpiresAt: LocalDateTime,
    )

    data class ErrorResponse(
        val message: String,
        val errorCode: String,
    )

    // ---- Endpoints ----

    @PostMapping("/register")
    fun register(@RequestBody request: RegisterRequest): ResponseEntity<*> {
        val cmd =
            UserRegisterCMD(
                phoneNumber = PhoneNumber(request.phoneNumber),
                nickname = request.nickname,
                rawPassword = request.password,
            )
        return userAccountService.register(cmd).toResponse { account ->
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

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<*> {
        return userAccountService
            .login(
                phoneNumber = PhoneNumber(request.phoneNumber),
                rawPassword = request.password,
            )
            .toResponse { tokenPair ->
                TokenResponse(
                    accessToken = tokenPair.accessToken,
                    accessTokenExpiresAt = tokenPair.accessTokenExpiresAt,
                    refreshToken = tokenPair.refreshToken,
                    refreshTokenExpiresAt = tokenPair.refreshTokenExpiresAt,
                )
            }
    }

    @PostMapping("/refresh-token")
    fun refreshToken(@RequestBody request: RefreshTokenRequest): ResponseEntity<*> {
        return userAccountService.refreshToken(request.refreshToken).toResponse { tokenPair ->
            TokenResponse(
                accessToken = tokenPair.accessToken,
                accessTokenExpiresAt = tokenPair.accessTokenExpiresAt,
                refreshToken = tokenPair.refreshToken,
                refreshTokenExpiresAt = tokenPair.refreshTokenExpiresAt,
            )
        }
    }

    @GetMapping("/{id}")
    fun findById(@PathVariable id: Long): ResponseEntity<*> {
        return userAccountService.findById(UserId(id)).toResponse { account ->
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

    @PutMapping("/{id}/nickname")
    fun changeNickname(
        @PathVariable id: Long,
        @RequestBody request: ChangeNicknameRequest,
    ): ResponseEntity<*> {
        return userAccountService
            .changeNickname(
                userId = UserId(id),
                newNickname = Nickname(request.nickname),
            )
            .toResponse {}
    }

    @PutMapping("/{id}/password")
    fun changePassword(
        @PathVariable id: Long,
        @RequestBody request: ChangePasswordRequest,
    ): ResponseEntity<*> {
        return userAccountService
            .changePassword(
                userId = UserId(id),
                oldPassword = request.oldPassword,
                newPassword = request.newPassword,
            )
            .toResponse {}
    }

    @PostMapping("/{id}/disable")
    fun disable(@PathVariable id: Long): ResponseEntity<*> {
        return userAccountService.disable(UserId(id)).toResponse {}
    }

    @PostMapping("/{id}/enable")
    fun enable(@PathVariable id: Long): ResponseEntity<*> {
        return userAccountService.enable(UserId(id)).toResponse {}
    }

    @PostMapping("/{id}/force-offline")
    fun forceOffline(@PathVariable id: Long): ResponseEntity<*> {
        return userAccountService.forceOffline(UserId(id)).toResponse {}
    }

    // ---- Helper ----

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
