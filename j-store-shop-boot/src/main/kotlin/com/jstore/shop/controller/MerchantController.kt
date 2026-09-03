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
package com.jstore.shop.controller

import com.jstore.authentication.annotation.CurrentPrincipal
import com.jstore.authentication.annotation.RequireLogin
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.common.errors.BusinessError
import com.jstore.common.utils.Result
import com.jstore.common.utils.fold
import com.jstore.shop.domain.merchant.Merchant
import com.jstore.shop.domain.merchant.MerchantId
import com.jstore.shop.domain.merchant.MerchantMembership
import com.jstore.shop.domain.merchant.MerchantRole
import com.jstore.shop.service.MerchantAccountView
import com.jstore.shop.service.MerchantUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/merchants")
@RequireLogin
class MerchantController(private val service: MerchantUseCase) {
    data class CreateMerchantRequest(@field:NotBlank @field:Size(max = 128) val name: String)

    data class AddMemberRequest(
        @field:Positive val userId: Long,
        @field:NotEmpty val roles: Set<MerchantRole>,
    )

    data class ChangeRolesRequest(@field:NotEmpty val roles: Set<MerchantRole>)

    data class MerchantResponse(val id: Long, val name: String, val status: String)

    data class MerchantAccountResponse(
        val merchantId: Long,
        val name: String,
        val merchantStatus: String,
        val membershipStatus: String,
        val roles: List<String>,
    )

    data class MemberResponse(
        val merchantId: Long,
        val userId: Long,
        val status: String,
        val roles: List<String>,
    )

    data class ErrorResponse(val message: String, val errorCode: String)

    @PostMapping
    fun create(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @Valid @RequestBody request: CreateMerchantRequest,
    ): ResponseEntity<*> =
        service.create(principal.accountId.value, request.name).response(HttpStatus.CREATED) {
            it.response()
        }

    @GetMapping
    fun listMine(
        @CurrentPrincipal principal: AuthenticatedPrincipal
    ): ResponseEntity<List<MerchantAccountResponse>> =
        ResponseEntity.ok(service.listForUser(principal.accountId.value).map { it.response() })

    @PostMapping("/{merchantId}/members")
    fun addMember(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable merchantId: Long,
        @Valid @RequestBody request: AddMemberRequest,
    ): ResponseEntity<*> =
        service
            .addMember(
                principal.accountId.value,
                MerchantId(merchantId),
                request.userId,
                request.roles,
            )
            .response(HttpStatus.CREATED) { it.response() }

    @PutMapping("/{merchantId}/members/{userId}/roles")
    fun changeRoles(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable merchantId: Long,
        @PathVariable userId: Long,
        @Valid @RequestBody request: ChangeRolesRequest,
    ): ResponseEntity<*> =
        service
            .changeMemberRoles(
                principal.accountId.value,
                MerchantId(merchantId),
                userId,
                request.roles,
            )
            .response { it.response() }

    @DeleteMapping("/{merchantId}/members/{userId}")
    fun disableMember(
        @CurrentPrincipal principal: AuthenticatedPrincipal,
        @PathVariable merchantId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<*> =
        service.disableMember(principal.accountId.value, MerchantId(merchantId), userId).response {
            mapOf("disabled" to true)
        }

    private fun Merchant.response() = MerchantResponse(id.value, name, status.name)

    private fun MerchantAccountView.response() =
        MerchantAccountResponse(
            merchant.id.value,
            merchant.name,
            merchant.status.name,
            membership.status.name,
            membership.roles.map { it.name }.sorted(),
        )

    private fun MerchantMembership.response() =
        MemberResponse(merchantId.value, userId, status.name, roles.map { it.name }.sorted())

    private fun <T> Result<T, BusinessError>.response(
        successStatus: HttpStatus = HttpStatus.OK,
        mapper: (T) -> Any,
    ): ResponseEntity<*> =
        fold(
            onSuccess = { ResponseEntity.status(successStatus).body(mapper(it)) },
            onFailure = {
                ResponseEntity.status(it.httpCode).body(ErrorResponse(it.message, it.errorCode))
            },
        )
}
