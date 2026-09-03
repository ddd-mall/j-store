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
package com.jstore.authentication.context

import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.user.domain.useraccount.UserId
import io.kotest.common.ExperimentalKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch

// Feature: authentication-sdk, Property 4: AuthenticatedUserContext 存取 round-trip
@OptIn(ExperimentalKotest::class)
class AuthenticatedUserContextPropertyTest :
    FunSpec({

        // **Validates: Requirements 5.1, 5.3, 5.5**
        test(
            "set → getCurrentUserId → getCurrentUserIdOrNull → clear → getCurrentUserIdOrNull round-trip"
        ) {
            val userIdArb = Arb.long(min = 1L).map { UserId(it) }

            checkAll(PropTestConfig(iterations = 100), userIdArb) { userId ->
                try {
                    val principal = AuthenticatedPrincipal("issuer-a", userId)
                    AuthenticatedPrincipalContext.set(principal)
                    AuthenticatedPrincipalContext.getCurrent() shouldBe principal
                    AuthenticatedPrincipalContext.getCurrentOrNull() shouldBe principal
                    AuthenticatedPrincipalContext.clear()
                    AuthenticatedPrincipalContext.getCurrentOrNull().shouldBeNull()
                } finally {
                    AuthenticatedPrincipalContext.clear()
                }
            }
        }

        // Feature: authentication-sdk, Property 5: AuthenticatedUserContext 线程隔离
        // **Validates: Requirements 5.2**
        test("thread isolation — each thread sees only its own UserId") {
            val userIdPairArb = Arb.long(min = 1L).map { UserId(it) }

            checkAll(PropTestConfig(iterations = 100), userIdPairArb, userIdPairArb) {
                userIdA,
                userIdB ->
                val barrier = CountDownLatch(2)

                val futureA = CompletableFuture.supplyAsync {
                    try {
                        val principal = AuthenticatedPrincipal("issuer-a", userIdA)
                        AuthenticatedPrincipalContext.set(principal)
                        barrier.countDown()
                        barrier.await()
                        AuthenticatedPrincipalContext.getCurrent()
                    } finally {
                        AuthenticatedPrincipalContext.clear()
                    }
                }

                val futureB = CompletableFuture.supplyAsync {
                    try {
                        val principal = AuthenticatedPrincipal("issuer-b", userIdB)
                        AuthenticatedPrincipalContext.set(principal)
                        barrier.countDown()
                        barrier.await()
                        AuthenticatedPrincipalContext.getCurrent()
                    } finally {
                        AuthenticatedPrincipalContext.clear()
                    }
                }

                futureA.get() shouldBe AuthenticatedPrincipal("issuer-a", userIdA)
                futureB.get() shouldBe AuthenticatedPrincipal("issuer-b", userIdB)
            }
        }
    })
