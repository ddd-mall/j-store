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
package com.jstore.common.properties

import com.jstore.common.framework.Identifier

open class Id<T>(open val value: T) : Identifier {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (other != null && this::class == other::class && other is Id<*> && value == other.value)

    override fun hashCode(): Int = 31 * this::class.hashCode() + value.hashCode()

    override fun toString(): String = value.toString()
}
