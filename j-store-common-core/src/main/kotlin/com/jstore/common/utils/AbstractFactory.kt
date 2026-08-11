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
package com.jstore.common.utils

import com.jstore.common.errors.CommonErrors
import com.jstore.common.logging.LoggerFactory
import java.lang.reflect.Constructor

abstract class AbstractFactory<T : Any>(private val candidateClass: Collection<Class<out T>>) {
    private val log = LoggerFactory.getLogger(this::class)
    @Volatile private var lock = Any()
    private val candidateMap: MutableMap<String, Constructor<out T>> = HashMap()

    private fun getCandidateMapKey(args: Array<out Any>): String {
        return args.map { it::class.java.name }.toString()
    }

    private fun election(args: Array<out Any>): T {
        synchronized(lock) {
            candidateMap[getCandidateMapKey(args)]?.let {
                try {
                    return it.newInstance(*args)
                } catch (e: Exception) {
                    // Try the configured candidates below when the cached constructor is stale.
                }
            }
            for (candidateClazz in candidateClass) {
                try {
                    val candidateConstructor =
                        candidateClazz.getDeclaredConstructor(
                            *args.map { it::class.java }.toTypedArray()
                        )
                    candidateConstructor.isAccessible = true
                    val newInstance = candidateConstructor.newInstance(*args)
                    candidateMap[getCandidateMapKey(args)] = candidateConstructor
                    log.info("candidate through election: $candidateClazz")
                    return newInstance
                } catch (e: Exception) {
                    // ignore
                }
            }
            throw IllegalStateException("No implementation were found")
        }
    }

    protected fun newInstance(vararg args: Any): T {
        candidateMap[getCandidateMapKey(args)]?.let {
            try {
                return it.newInstance(args)
            } catch (e: Exception) {
                // ignore
            }
        }
        return try {
            election(args)
        } catch (e: Exception) {
            log.error("Error creating instance. Cause: $e", e)
            throw CommonErrors.INTERNAL_ERROR.msgAndCause("Error creating instance.  Cause: $e", e)
        }
    }
}
