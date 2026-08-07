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

class BusinessError(
    val message: String,
    val errorCode: String,
    val httpCode: Int,
) {

    fun msg(message: String): BusinessError {
        return BusinessError(message, this.errorCode, this.httpCode)
    }
}

object CommonBusinessError {
    val INVALID_PARAM: BusinessError = BusinessError("非法参数", "Parameters.Invalid", 400)
    val ILLEGAL_STATE: BusinessError = BusinessError("非法状态", "App.IllegalState", 500)
    val INTERNAL_ERROR: BusinessError = BusinessError("内部错误", "App.InternalError", 500)
    val CONCURRENT_CONFLICT_EXCEPTION =
        BusinessError("系统繁忙，请稍后重试", "App.concurrentialConflict", 500)
    val OBJECT_NOT_FOUNT = BusinessError("未能找到资源", "Biz.Object Not Found", 404)
}
