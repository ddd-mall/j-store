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

import com.jstore.authentication.principal.AuthenticatedAccountId
import com.jstore.authentication.principal.AuthenticatedPrincipal
import com.jstore.authentication.principal.AuthenticatedSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AuthenticatedPrincipalContractTest :
    FunSpec({
        test("principal keeps authentication domain separate from domain-local user id") {
            val siteA =
                AuthenticatedPrincipal(
                    "https://accounts.site-a.example",
                    AuthenticatedAccountId(42),
                    AuthenticatedSession("session-a", 1),
                )
            val siteB =
                AuthenticatedPrincipal(
                    "https://accounts.site-b.example",
                    AuthenticatedAccountId(42),
                    AuthenticatedSession("session-b", 1),
                )

            (siteA == siteB) shouldBe false
            siteA.accountId shouldBe siteB.accountId
        }

        test("authenticated principal context round-trips the complete principal") {
            val principal =
                AuthenticatedPrincipal(
                    "https://accounts.site-a.example",
                    AuthenticatedAccountId(7),
                    AuthenticatedSession("session-7", 3),
                )

            AuthenticatedPrincipalContext.set(principal)
            try {
                AuthenticatedPrincipalContext.getCurrent() shouldBe principal
            } finally {
                AuthenticatedPrincipalContext.clear()
            }
        }
    })
