package com.jstore.order.config

import java.util.concurrent.Executor

interface ExecutorFactory {
    fun get(): Executor
}