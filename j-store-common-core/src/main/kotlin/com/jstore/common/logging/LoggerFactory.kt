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

import com.jstore.common.logging.slf4j.Slf4jSimpleImpl
import java.lang.reflect.Constructor
import kotlin.reflect.KClass

object LoggerFactory {
    const val MARKER: String = "JSTORE"
    private var loggerConstructor: Constructor<out Logger>? = null

    init {
        tryImplementation { useSlf4jLogging() }
    }

    fun <T> getLogger(clazz: Class<T>): Logger {
        return getLogger(clazz.name)
    }

    fun getLogger(clazz: KClass<*>): Logger {
        return getLogger(clazz.java)
    }

    fun getLogger(name: String): Logger {
        if (null == loggerConstructor) {
            throw LogException(
                "Error creating logger for logger $name.  Cause: can not find Logger implementation"
            )
        }
        try {
            return loggerConstructor!!.newInstance(name)
        } catch (t: Throwable) {
            throw LogException("Error creating logger for logger $name.  Cause: $t", t)
        }
    }

    private fun useSlf4jLogging() {
        setImplementation(Slf4jSimpleImpl::class.java)
    }

    private fun tryImplementation(r: Runnable) {
        if (null == loggerConstructor) {
            try {
                r.run()
            } catch (t: Throwable) {
                // ignore
            }
        }
    }

    private fun setImplementation(implClass: Class<out Logger>) {
        try {
            val con = implClass.getConstructor(String::class.java)
            val clazzName = LoggerFactory::class.java.name

            val log = con.newInstance(clazzName)
            if (log.isDebugEnabled()) {
                log.debug("Logging initialized using '$implClass' adapter.")
            }
            loggerConstructor = con
        } catch (e: Throwable) {
            throw LogException("Error setting Log implementation.  Cause: $e", e)
        }
    }
}
