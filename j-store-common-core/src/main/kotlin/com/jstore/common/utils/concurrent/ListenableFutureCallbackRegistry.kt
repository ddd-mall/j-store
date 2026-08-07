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

import java.util.*

/** 抄spring的 */
class ListenableFutureCallbackRegistry<T> {
    private val successCallBacks: Queue<SuccessCallback<in T>> = LinkedList()
    private val failureCallBacks: Queue<FailureCallback> = LinkedList()

    @Volatile private var state: State = State.NEW
    private var result: T? = null
    private var failureResult: Throwable? = null
    private val mutex = Any()

    fun addCallback(callback: ListenableFutureCallback<in T>) {
        synchronized(mutex) {
            when (state) {
                State.NEW -> {
                    successCallBacks.add(callback)
                    failureCallBacks.add(callback)
                    return
                }

                State.SUCCESS -> {
                    notifySuccess(callback)
                    return
                }

                State.FAILURE -> {
                    notifyFailure(callback)
                    return
                }
            }
        }
    }

    fun addSuccessCallback(callback: SuccessCallback<in T>) {
        synchronized(mutex) {
            when (state) {
                State.NEW -> {
                    successCallBacks.add(callback)
                    return
                }

                State.SUCCESS -> {
                    notifySuccess(callback)
                    return
                }

                State.FAILURE -> {
                    return
                }
            }
        }
    }

    fun addFailureCallback(callback: FailureCallback) {
        synchronized(mutex) {
            when (state) {
                State.NEW -> {
                    failureCallBacks.add(callback)
                    return
                }

                State.SUCCESS -> {
                    return
                }

                State.FAILURE -> {
                    notifyFailure(callback)
                    return
                }
            }
        }
    }

    private fun notifySuccess(callback: SuccessCallback<in T>) {
        try {
            callback.onSuccess(this.result)
        } catch (e: Throwable) {
            // ignore
        }
    }

    private fun notifyFailure(callback: FailureCallback) {
        try {
            callback.onFailure(failureResult)
        } catch (e: Throwable) {
            // ignore
        }
    }

    fun success(result: T) {
        synchronized(mutex) {
            this.state = State.SUCCESS
            this.result = result
            var callBack: SuccessCallback<in T>?
            do {
                callBack = successCallBacks.poll()
                callBack?.let { notifySuccess(it) }
            } while (callBack != null)
        }
    }

    fun failure(error: Throwable) {
        synchronized(mutex) {
            this.state = State.FAILURE
            this.failureResult = error
            var callBack: FailureCallback?
            do {
                callBack = failureCallBacks.poll()
                callBack?.let { notifyFailure(it) }
            } while (callBack != null)
        }
    }

    private enum class State {
        NEW,
        SUCCESS,
        FAILURE,
    }
}
