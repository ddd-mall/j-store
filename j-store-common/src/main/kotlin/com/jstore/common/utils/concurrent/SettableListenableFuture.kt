package com.jstore.common.utils.concurrent

import java.util.concurrent.Callable
import java.util.concurrent.TimeUnit


open class SettableListenableFuture<T> : ListenableFuture<T> {
    companion object {
        private val DUMMY_CALLABLE: Callable<out Any> = Callable<Any> { throw IllegalStateException("Should never be called") }
        private fun <T> dummyCallable(): Callable<T> {
            @Suppress("UNCHECKED_CAST")
            val result =  DUMMY_CALLABLE as Callable<T>
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

    override fun addCallback(successCallback: SuccessCallback<in T>, failureCallback: FailureCallback) {
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