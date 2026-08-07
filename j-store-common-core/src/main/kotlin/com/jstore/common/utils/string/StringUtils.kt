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
package com.jstore.common.utils.string

object StringUtils {

    fun isBlank(cs: CharSequence?): Boolean {
        return isEmpty(cs)
    }

    fun isNotBlank(cs: CharSequence?): Boolean {
        return !isBlank(cs)
    }

    fun isEmpty(cs: CharSequence?): Boolean {
        if (cs == null) {
            return true
        }
        val length = cs.length
        if (length > 0) {
            for (i in 0 until length) {
                if (!Character.isWhitespace(cs[i])) {
                    return false
                }
            }
        }
        return true
    }

    fun isNotEmpty(cs: CharSequence?): Boolean {
        return !isEmpty(cs)
    }

    fun isAllEmpty(vararg css: CharSequence?): Boolean {
        for (cs in css) {
            if (isNotEmpty(cs)) {
                return false
            }
        }
        return true
    }
}
