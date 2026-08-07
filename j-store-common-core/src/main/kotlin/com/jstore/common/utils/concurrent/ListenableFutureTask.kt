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
package com.jstore.common.utils.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask

open class ListenableFutureTask<T> : FutureTask<T>, ListenableFuture<T> {
    constructor(callable: Callable<T>) : super(callable)

    constructor(runnable: Runnable, result: T) : super(runnable, result)

    private val callbacks: ListenableFutureCallbackRegistry<T> = ListenableFutureCallbackRegistry()

    override fun addCallback(callback: ListenableFutureCallback<in T>) {
        callbacks.addCallback(callback)
    }

    override fun addCallback(
        successCallback: SuccessCallback<in T>,
        failureCallback: FailureCallback,
    ) {
        callbacks.addSuccessCallback(successCallback)
        callbacks.addFailureCallback(failureCallback)
    }

    override fun done() {
        var cause: Throwable? = null
        try {
            val result: T = get()
            callbacks.success(result)
            return
        } catch (ex: ExecutionException) {
            cause = ex.cause
        } catch (ex: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (ex: Throwable) {
            cause = ex
        }
        callbacks.failure(cause!!)
    }
}
