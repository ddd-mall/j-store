package com.jstore.common.utils.concurrent

import java.util.concurrent.Future

interface ListenableFuture<T> : Future<T> {
    fun addCallback(callback: ListenableFutureCallback<in T>)

    fun addCallback(successCallback: SuccessCallback<in T>, failureCallback: FailureCallback)
}

interface ListenableFutureCallback<T> : SuccessCallback<T>, FailureCallback

interface SuccessCallback<T> {
    fun onSuccess(result: T?)
}

interface FailureCallback {
    fun onFailure(ex: Throwable?)
}
