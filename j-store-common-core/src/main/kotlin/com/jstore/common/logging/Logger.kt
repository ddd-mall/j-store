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
package com.jstore.common.logging

interface Logger {
    fun isDebugEnabled(): Boolean

    fun debug(msg: String)

    fun debug(format: String, arg: Any)

    fun debug(format: String, throwable: Throwable)

    fun debug(format: String, vararg args: Any)

    fun info(msg: String)

    fun info(format: String, arg: Any)

    fun info(format: String, throwable: Throwable)

    fun info(format: String, vararg args: Any)

    fun warn(msg: String)

    fun warn(format: String, arg: Any)

    fun warn(format: String, throwable: Throwable)

    fun warn(format: String, vararg args: Any)

    fun error(msg: String)

    fun error(format: String, arg: Any)

    fun error(format: String, throwable: Throwable)

    fun error(format: String, vararg args: Any)
}
