package com.jstore.common.framework

import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

interface ExecutorServiceFactory {
    fun get(): ThreadPoolTaskExecutor
}


