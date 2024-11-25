package com.jstore.common.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory


object Logger {
    private val logger: Logger = LoggerFactory.getLogger(com.jstore.common.utils.Logger.javaClass)
    fun info(msg: String) {
        logger.info(msg)
    }

    fun info(msg: String, arg: Any) {
        logger.info(msg, arg)
    }

    fun info(msg: String, args: Array<Any>) {
        logger.info(msg, *args)
    }
}