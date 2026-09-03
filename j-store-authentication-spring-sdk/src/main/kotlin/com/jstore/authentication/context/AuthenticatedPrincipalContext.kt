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

object AuthenticatedPrincipalContext {
    private val holder: ThreadLocal<AuthenticatedPrincipal> = ThreadLocal()

    fun set(principal: AuthenticatedPrincipal) {
        holder.set(principal)
    }

    fun getCurrent(): AuthenticatedPrincipal =
        holder.get() ?: throw AuthenticationException("当前上下文中无已认证主体")

    fun getCurrentOrNull(): AuthenticatedPrincipal? = holder.get()

    fun clear() {
        holder.remove()
    }
}
