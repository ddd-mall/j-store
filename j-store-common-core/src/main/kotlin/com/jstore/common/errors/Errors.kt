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
package com.jstore.common.errors

open class Errors : RuntimeException {
    private val msg: String
    private val errorCode: String
    private val httpCode: Int

    constructor(
        msg: String,
        errorCode: String,
        httpCode: Int,
        thrown: Throwable,
    ) : super(msg, thrown) {
        this.msg = msg
        this.errorCode = errorCode
        this.httpCode = httpCode
    }

    constructor(msg: String, errorCode: String, httpCode: Int) : super(msg) {
        this.msg = msg
        this.errorCode = errorCode
        this.httpCode = httpCode
    }

    fun msg(msg: String): Errors {
        return Errors(msg, this.errorCode, this.httpCode)
    }

    fun msgAndCause(msg: String, cause: Throwable): Errors {
        return Errors(msg, this.errorCode, this.httpCode, cause)
    }

    fun cause(cause: Throwable): Errors {
        return Errors(this.msg, this.errorCode, this.httpCode, cause)
    }
}

object CommonErrors {
    val INVALID_PARAM: Errors = Errors("非法参数", "Parameters.Invalid", 400)
    val ILLEGAL_STATE: Errors = Errors("非法状态", "App.IllegalState", 500)
    val INTERNAL_ERROR: Errors = Errors("内部错误", "App.InternalError", 500)
    val OBJECT_NOT_FOUND: Errors = Errors("访问的资源不存在", "Resource.notfound", 404)
}
