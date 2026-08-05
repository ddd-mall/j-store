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
