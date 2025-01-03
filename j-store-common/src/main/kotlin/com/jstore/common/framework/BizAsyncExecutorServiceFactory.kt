package com.jstore.common.framework

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

interface ExecutorServiceFactory {
    fun get(): ExecutorService

}

object BizAsyncExecutorServiceFactory : ExecutorServiceFactory {
    private val executorService = Executors.newCachedThreadPool()

    override fun get(): ExecutorService {
        return executorService
    }
}