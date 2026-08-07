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
import java.util.concurrent.TimeUnit

open class SettableListenableFuture<T> : ListenableFuture<T> {
    companion object {
        private val DUMMY_CALLABLE: Callable<out Any> =
            Callable<Any> { throw IllegalStateException("Should never be called") }

        private fun <T> dummyCallable(): Callable<T> {
            @Suppress("UNCHECKED_CAST") val result = DUMMY_CALLABLE as Callable<T>
            return result
        }
    }

    private val task: SettableTask<T> = SettableTask()

    fun set(value: T): Boolean {
        return task.setResult(value)
    }

    fun setException(exception: Throwable): Boolean {
        return task.setExceptionResult(exception)
    }

    override fun addCallback(callback: ListenableFutureCallback<in T>) {
        this.task.addCallback(callback)
    }

    override fun addCallback(
        successCallback: SuccessCallback<in T>,
        failureCallback: FailureCallback,
    ) {
        this.task.addCallback(successCallback, failureCallback)
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        val canceled = task.cancel(mayInterruptIfRunning)
        if (canceled && mayInterruptIfRunning) {
            interrupt()
        }
        return canceled
    }

    override fun isCancelled(): Boolean {
        return task.isCancelled
    }

    override fun isDone(): Boolean {
        return task.isDone
    }

    override fun get(): T {
        return task.get()
    }

    override fun get(timeout: Long, unit: TimeUnit): T {
        return task.get(timeout, unit)
    }

    protected fun interrupt() {}

    private class SettableTask<T> : ListenableFutureTask<T>(dummyCallable()) {
        @Volatile private var completingThread: Thread? = null

        fun setResult(result: T): Boolean {
            set(result)
            return checkCompletingThread()
        }

        fun setExceptionResult(exception: Throwable): Boolean {
            setException(exception)
            return checkCompletingThread()
        }

        override fun done() {
            if (!isCancelled) {
                this.completingThread = Thread.currentThread()
            }
            super.done()
        }

        private fun checkCompletingThread(): Boolean {
            val check = (this.completingThread === Thread.currentThread())
            if (check) {
                this.completingThread = null // only first match actually counts
            }
            return check
        }
    }
}
