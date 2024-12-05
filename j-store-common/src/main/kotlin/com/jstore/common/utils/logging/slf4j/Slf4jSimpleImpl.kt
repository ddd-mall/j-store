package com.jstore.common.utils.logging.slf4j

import com.jstore.common.utils.logging.Log
import com.jstore.common.utils.logging.LoggerFactory
import org.slf4j.LoggerFactory
import org.slf4j.Marker
import org.slf4j.MarkerFactory
import org.slf4j.spi.LocationAwareLogger

class Slf4jSimpleImpl(clazz: String) : Log {
    private val log: Log


    init {
        val logger = LoggerFactory.getLogger(clazz)
        var logTemporary: Log
        if (logger is LocationAwareLogger) {
            try {
                logger::class.java.getDeclaredMethod("log", Marker::class.java, String::class.java, Int::class.java, String::class.java, Array<Any>::class.java, Throwable::class.java)
                logTemporary = Slf4jLocationAwareLoggerImpl(logger)
            } catch (e: NoSuchMethodException) {
                logTemporary = Slf4jLoggerImpl(logger)
            }
            this.log = logTemporary
        } else {
            this.log = Slf4jLoggerImpl(logger)
        }
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

class Slf4jLoggerImpl(private val logger: org.slf4j.Logger): Log {
    override fun info(format: String, args: Array<out Any>) {
        logger.info(format, *args)
    }

    override fun info(format: String, arg: Any) {
        logger.info(format, arg)
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
        logger.debug(format, arg)
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
        logger.warn(format, arg)
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
        logger.error(format, arg)
    }

    override fun error(format: String, throwable: Throwable) {
        logger.error(format, throwable)
    }

    override fun error(format: String, args: Array<out Any>) {
        logger.error(format, *args)
    }
}

class Slf4jLocationAwareLoggerImpl(private val logger: LocationAwareLogger): Log {
    companion object {
        private val MARKER: Marker = MarkerFactory.getMarker(LoggerFactory.MARKER)
        private val FQCN = Slf4jLocationAwareLoggerImpl::class.qualifiedName;
    }


    override fun info(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, args, null)
    }

    override fun info(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, arrayOf(arg), null)
    }

    override fun info(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, format, null, throwable)
    }

    override fun info(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.INFO_INT, msg, null, null)
    }

    override fun debug(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, msg, null, null)
    }

    override fun debug(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, null, null)
    }

    override fun debug(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, null, throwable)
    }

    override fun debug(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.DEBUG_INT, format, args, null)
    }

    override fun warn(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, msg, null, null)
    }

    override fun warn(format: String, arg: Any) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, arrayOf(arg), null)
    }

    override fun warn(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, null, throwable)
    }

    override fun warn(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.WARN_INT, format, args, null)
    }

    override fun error(msg: String) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, msg, null, null)
    }

    override fun error(format: String, arg: Any) {

        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, arrayOf(arg), null)
    }

    override fun error(format: String, throwable: Throwable) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, null, throwable)
    }

    override fun error(format: String, args: Array<out Any>) {
        logger.log(MARKER, FQCN, LocationAwareLogger.ERROR_INT, format, args, null)
    }

}