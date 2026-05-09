package com.jstore.common.logging.slf4j

import com.jstore.common.logging.Logger
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.spi.LocationAwareLogger

class Slf4jSimpleImpl(clazz: String) : Logger {
    private val log: Logger
    init {
        val logger = LoggerFactory.getLogger(clazz)
        var loggerTemporary: Logger
        if (logger is LocationAwareLogger) {
            try {
                logger::class.java.getDeclaredMethod("log", Marker::class.java, String::class.java, Int::class.java, String::class.java, Array<Any>::class.java, Throwable::class.java)
                loggerTemporary = Slf4jLocationAwareLoggerImpl(logger)
            } catch (e: NoSuchMethodException) {
                loggerTemporary = Slf4jLoggerImpl(logger)
            }
            this.log = loggerTemporary
        } else {
            this.log = Slf4jLoggerImpl(logger)
        }
    }

    override fun isDebugEnabled(): Boolean {
        return log.isDebugEnabled()
    }

    override fun info(format: String, args: Array<out Any>) {
        log.info(format, args)
    }

    override fun info(format: String, arg: Any) {
        log.info(format, arg)
    }

    override fun info(format: String, throwable: Throwable) {
        log.info(format, throwable)
    }

    override fun info(msg: String) {
        log.info(msg)
    }

    override fun debug(msg: String) {
        log.debug(msg)
    }

    override fun debug(format: String, arg: Any) {
        log.debug(format, arg)
    }

    override fun debug(format: String, throwable: Throwable) {
        log.debug(format, throwable)
    }

    override fun debug(format: String, args: Array<out Any>) {
        log.debug(format, args)
    }

    override fun warn(msg: String) {
        log.warn(msg)
    }

    override fun warn(format: String, arg: Any) {
        log.warn(format, arg)
    }

    override fun warn(format: String, throwable: Throwable) {
        log.warn(format, throwable)
    }

    override fun warn(format: String, args: Array<out Any>) {
        log.warn(format, args)
    }

    override fun error(msg: String) {
        log.error(msg)
    }

    override fun error(format: String, arg: Any) {
        log.error(format, arg)
    }

    override fun error(format: String, throwable: Throwable) {
        log.error(format, throwable)
    }

    override fun error(format: String, args: Array<out Any>) {
        log.error(format, args)
    }
}

class Slf4jLoggerImpl(private val logger: org.slf4j.Logger): Logger {
    override fun isDebugEnabled(): Boolean {
        return logger.isDebugEnabled
    }

    override fun info(format: String, args: Array<out Any>) {
        logger.info(format, *args)
    }

    override fun info(format: String, arg: Any) {
        if (arg is Array<*>) {
            logger.info(format, *arg)
        } else {
            logger.info(format, arg)
        }
    }

    override fun info(format: String, throwable: Throwable) {
        logger.info(format, throwable)
    }

    override fun info(msg: String) {
        logger.info(msg)
    }

    override fun debug(msg: String) {
        logger.debug(msg)
    }

    override fun debug(format: String, arg: Any) {
        if (arg is Array<*>) {
            logger.debug(format, *arg)
        } else {
            logger.debug(format, arg)
        }
    }

    override fun debug(format: String, throwable: Throwable) {
        logger.debug(format, throwable)
    }

    override fun debug(format: String, args: Array<out Any>) {
        logger.debug(format, *args)
    }

    override fun warn(msg: String) {
        logger.warn(msg)
    }

    override fun warn(format: String, arg: Any) {
        if (arg is Array<*>) {
            logger.warn(format, *arg)
        } else {
            logger.warn(format, arg)
        }
    }

    override fun warn(format: String, throwable: Throwable) {
        logger.warn(format, throwable)
    }

    override fun warn(format: String, args: Array<out Any>) {
        logger.warn(format, *args)
    }

    override fun error(msg: String) {
        logger.error(msg)
    }

    override fun error(format: String, arg: Any) {
        if (arg is Array<*>) {
            logger.error(format, *arg)
        } else {
            logger.error(format, arg)
        }
    }

    override fun error(format: String, throwable: Throwable) {
        logger.error(format, throwable)
    }

    override fun error(format: String, args: Array<out Any>) {
        logger.error(format, *args)
    }
}

